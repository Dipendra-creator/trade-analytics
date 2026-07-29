#!/usr/bin/env python3
import argparse
import json
import math
import os
import statistics
import sys
from collections import defaultdict, deque
from dataclasses import asdict, dataclass
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from pathlib import Path

import mysql.connector


@dataclass
class Candle:
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float


@dataclass
class Position:
    symbol: str
    side: str
    signal_at: datetime
    opened_at: datetime
    entry: float
    stop: float
    target: float
    quantity: int
    confidence: float
    score: float


@dataclass
class ClosedTrade:
    symbol: str
    side: str
    signal_at: str
    opened_at: str
    closed_at: str
    entry: float
    exit: float
    quantity: int
    gross_pnl: float
    costs: float
    net_pnl: float
    reason: str
    confidence: float
    score: float


def arguments():
    parser = argparse.ArgumentParser(description="Deterministic N50 paper-strategy qualification")
    parser.add_argument("--from", dest="from_date", required=True, help="Inclusive YYYY-MM-DD")
    parser.add_argument("--to", dest="to_date", required=True, help="Exclusive YYYY-MM-DD")
    parser.add_argument("--phase", choices=("development", "validation", "out_of_sample"), required=True)
    parser.add_argument("--output", default="qualification-result.json")
    parser.add_argument("--starting-capital", type=float, default=1_000_000)
    parser.add_argument("--risk-per-trade", type=float, default=0.01)
    parser.add_argument("--max-open", type=int, default=5)
    parser.add_argument("--max-daily-loss", type=float, default=0.03)
    parser.add_argument("--max-hold-minutes", type=int, default=30)
    parser.add_argument("--slippage-bps", type=float, default=5)
    parser.add_argument("--transaction-cost-bps-per-side", type=float, default=8)
    return parser.parse_args()


def connection():
    return mysql.connector.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "13306")),
        database=os.getenv("MYSQL_DATABASE", "qualification"),
        user=os.getenv("MYSQL_USER", "qualification_user"),
        password=os.environ["MYSQL_PASSWORD"],
        autocommit=False,
    )


def nearest(history, at):
    for candle in reversed(history):
        if candle.timestamp <= at:
            return candle.close
    return None


def adverse_price(price, side, entry, slippage_bps):
    adverse_up = (side == "LONG") == entry
    fraction = slippage_bps / 10_000
    return price * (1 + fraction if adverse_up else 1 - fraction)


def exit_reason(position, candle, max_hold_minutes):
    if position.side == "LONG":
        if candle.low <= position.stop:
            return "STOP", position.stop
        if candle.high >= position.target:
            return "TARGET", position.target
    else:
        if candle.high >= position.stop:
            return "STOP", position.stop
        if candle.low <= position.target:
            return "TARGET", position.target
    if candle.timestamp - position.opened_at >= timedelta(minutes=max_hold_minutes):
        return "TIME", candle.close
    if candle.timestamp.time() >= time(15, 20):
        return "MARKET_CLOSE", candle.close
    return None, None


def metrics(closed, starting_capital):
    profits = [trade.net_pnl for trade in closed if trade.net_pnl > 0]
    losses = [-trade.net_pnl for trade in closed if trade.net_pnl < 0]
    net = sum(trade.net_pnl for trade in closed)
    profit_factor = sum(profits) / sum(losses) if losses else 0.0
    expectancy = net / len(closed) if closed else 0.0
    daily = defaultdict(float)
    monthly = defaultdict(float)
    for trade in closed:
        closed_at = datetime.fromisoformat(trade.closed_at)
        daily[closed_at.date().isoformat()] += trade.net_pnl
        monthly[closed_at.strftime("%Y-%m")] += trade.net_pnl
    daily_returns = [pnl / starting_capital for _, pnl in sorted(daily.items())]
    sharpe = 0.0
    if len(daily_returns) > 1 and statistics.stdev(daily_returns) > 0:
        sharpe = statistics.mean(daily_returns) / statistics.stdev(daily_returns) * math.sqrt(252)
    equity = starting_capital
    peak = starting_capital
    max_drawdown = 0.0
    for _, pnl in sorted(daily.items()):
        equity += pnl
        peak = max(peak, equity)
        max_drawdown = max(max_drawdown, (peak - equity) / peak if peak else 0)
    wins = len(profits)
    return {
        "tradeCount": len(closed),
        "wins": wins,
        "losses": len(losses),
        "winRate": wins / len(closed) if closed else 0.0,
        "netPnl": net,
        "endingEquity": starting_capital + net,
        "expectancy": expectancy,
        "profitFactor": profit_factor,
        "sharpe": sharpe,
        "maxDrawdownPercent": max_drawdown * 100,
        "totalCosts": sum(trade.costs for trade in closed),
        "monthlyPnl": dict(sorted(monthly.items())),
    }


def candidates_for(timestamp, candles, histories, previous_closes, weights, synthetic_previous_index):
    rows = []
    advances = declines = 0
    absolute_impact = 0.0
    for symbol, candle in candles.items():
        history = histories[symbol]
        p5 = nearest(history, timestamp - timedelta(minutes=5))
        p15 = nearest(history, timestamp - timedelta(minutes=15))
        p60 = nearest(history, timestamp - timedelta(minutes=60))
        previous = previous_closes.get(symbol)
        if not p5 or not p15 or not p60 or not previous:
            continue
        day_return = candle.close / previous - 1
        r5 = (candle.close / p5 - 1) * 100
        r15 = (candle.close / p15 - 1) * 100
        r60 = (candle.close / p60 - 1) * 100
        if day_return > 0.00005:
            advances += 1
        elif day_return < -0.00005:
            declines += 1
        contribution = synthetic_previous_index * weights[symbol] * day_return
        absolute_impact += abs(contribution)
        rows.append((symbol, candle, r5, r15, r60, contribution))
    coverage = len(rows)
    if coverage < len(weights):
        return []
    breadth = (advances - declines) / coverage
    ranked = []
    for symbol, candle, r5, r15, r60, contribution in rows:
        momentum = 0.50 * r5 + 0.32 * r15 + 0.18 * r60
        if abs(momentum) < 0.035 or abs(r5) < 0.015:
            continue
        side = "LONG" if momentum >= 0 else "SHORT"
        agreement = sum(1 for value in (r5, r15, r60) if math.copysign(1, value) == math.copysign(1, momentum))
        breadth_alignment = 5 if math.copysign(1, breadth or momentum) == math.copysign(1, momentum) else 0
        risk_score = weights[symbol] * abs(r5)
        confidence = max(35, min(88, 42 + agreement * 9 + min(18, abs(momentum) * 18)
                                 + breadth_alignment - min(8, risk_score * 12)))
        impact_share = abs(contribution) / absolute_impact * 100 if absolute_impact else 0
        score = abs(momentum) * confidence + abs(contribution) * 2 + impact_share * 0.08
        volatility_percent = max(0.22, abs(r5) * 1.7 + abs(r15) * 0.55)
        stop_distance = candle.close * max(0.0022, min(0.012, volatility_percent / 100))
        target_distance = stop_distance * 1.8
        stop = candle.close - stop_distance if side == "LONG" else candle.close + stop_distance
        target = candle.close + target_distance if side == "LONG" else candle.close - target_distance
        ranked.append({"symbol": symbol, "side": side, "signal_at": timestamp, "raw_entry": candle.close,
                       "stop": stop, "target": target, "confidence": confidence, "score": score})
    return sorted(ranked, key=lambda value: value["score"], reverse=True)[:6]


def run(args):
    db = connection()
    cursor = db.cursor(dictionary=True)
    cursor.execute("SELECT symbol, weight_percent FROM nifty50_constituent WHERE active=TRUE ORDER BY rank_number")
    weights_raw = {row["symbol"]: float(row["weight_percent"]) for row in cursor.fetchall()}
    weight_total = sum(weights_raw.values())
    weights = {symbol: value / weight_total for symbol, value in weights_raw.items()}
    cursor.close()
    stream = db.cursor(dictionary=True, buffered=False)
    stream.execute(
        """SELECT nc.symbol, sc.interval_start, sc.open_price, sc.high_price, sc.low_price, sc.close_price
           FROM stock_candle sc JOIN nifty50_constituent nc ON nc.id=sc.constituent_id
           WHERE nc.active=TRUE AND sc.interval_start >= %s AND sc.interval_start < %s
           ORDER BY sc.interval_start, nc.rank_number""",
        (args.from_date, args.to_date),
    )
    histories = {symbol: deque(maxlen=500) for symbol in weights}
    previous_closes = {}
    latest_closes = {}
    active_signals = set()
    pending = []
    positions = []
    closed = []
    daily_realized = defaultdict(float)
    synthetic_previous_index = 25_000.0
    current_day = None
    current_timestamp = None
    minute_candles = {}

    def process_minute(timestamp, candles):
        nonlocal active_signals, pending, positions, synthetic_previous_index
        if len(candles) != len(weights):
            for symbol, candle in candles.items():
                histories[symbol].append(candle)
                latest_closes[symbol] = candle.close
            return
        open_symbols = {position.symbol for position in positions}
        for signal in pending:
            candle = candles.get(signal["symbol"])
            if candle is None or signal["symbol"] in open_symbols or len(positions) >= args.max_open:
                continue
            equity = args.starting_capital + sum(trade.net_pnl for trade in closed)
            if daily_realized[timestamp.date()] <= -args.starting_capital * args.max_daily_loss:
                continue
            entry = adverse_price(candle.open, signal["side"], True, args.slippage_bps)
            risk_per_share = abs(entry - signal["stop"])
            quantity = min(int(equity * args.risk_per_trade / risk_per_share), int(equity / entry)) if risk_per_share else 0
            if quantity > 0:
                positions.append(Position(signal["symbol"], signal["side"], signal["signal_at"], timestamp,
                                          entry, signal["stop"], signal["target"], quantity,
                                          signal["confidence"], signal["score"]))
                open_symbols.add(signal["symbol"])
        pending = []
        still_open = []
        for position in positions:
            candle = candles[position.symbol]
            reason, raw_exit = exit_reason(position, candle, args.max_hold_minutes)
            if reason is None:
                still_open.append(position)
                continue
            exit_price = adverse_price(raw_exit, position.side, False, args.slippage_bps)
            direction = 1 if position.side == "LONG" else -1
            gross = (exit_price - position.entry) * direction * position.quantity
            costs = (position.entry + exit_price) * position.quantity * args.transaction_cost_bps_per_side / 10_000
            net = gross - costs
            daily_realized[timestamp.date()] += net
            closed.append(ClosedTrade(position.symbol, position.side, position.signal_at.isoformat(),
                                      position.opened_at.isoformat(), timestamp.isoformat(), position.entry,
                                      exit_price, position.quantity, gross, costs, net, reason,
                                      position.confidence, position.score))
        positions = still_open
        generated = candidates_for(timestamp, candles, histories, previous_closes, weights, synthetic_previous_index)
        current_keys = {(value["symbol"], value["side"]) for value in generated}
        existing_symbols = {position.symbol for position in positions} | {value["symbol"] for value in pending}
        if daily_realized[timestamp.date()] > -args.starting_capital * args.max_daily_loss:
            for signal in generated:
                key = (signal["symbol"], signal["side"])
                if key not in active_signals and signal["symbol"] not in existing_symbols:
                    pending.append(signal)
                    existing_symbols.add(signal["symbol"])
        active_signals = current_keys
        for symbol, candle in candles.items():
            histories[symbol].append(candle)
            latest_closes[symbol] = candle.close

    for row in stream:
        timestamp = row["interval_start"]
        if current_timestamp is not None and timestamp != current_timestamp:
            process_minute(current_timestamp, minute_candles)
            minute_candles = {}
        if current_day != timestamp.date():
            if current_day is not None:
                day_return = sum(weights[symbol] * (latest_closes[symbol] / previous_closes.get(symbol, latest_closes[symbol]) - 1)
                                 for symbol in latest_closes)
                synthetic_previous_index *= 1 + day_return
                previous_closes = dict(latest_closes)
            current_day = timestamp.date()
        current_timestamp = timestamp
        minute_candles[row["symbol"]] = Candle(timestamp, float(row["open_price"]), float(row["high_price"]),
                                                float(row["low_price"]), float(row["close_price"]))
    if current_timestamp is not None:
        process_minute(current_timestamp, minute_candles)
    stream.close()
    if positions and current_timestamp:
        final_candles = minute_candles
        for position in positions:
            candle = final_candles.get(position.symbol)
            if candle is None:
                continue
            exit_price = adverse_price(candle.close, position.side, False, args.slippage_bps)
            direction = 1 if position.side == "LONG" else -1
            gross = (exit_price - position.entry) * direction * position.quantity
            costs = (position.entry + exit_price) * position.quantity * args.transaction_cost_bps_per_side / 10_000
            closed.append(ClosedTrade(position.symbol, position.side, position.signal_at.isoformat(),
                                      position.opened_at.isoformat(), current_timestamp.isoformat(), position.entry,
                                      exit_price, position.quantity, gross, costs, gross - costs, "DATA_END",
                                      position.confidence, position.score))
    result_metrics = metrics(closed, args.starting_capital)
    gates = {
        "minimumTrades": result_metrics["tradeCount"] >= 300,
        "positiveExpectancy": result_metrics["expectancy"] > 0,
        "profitFactor": result_metrics["profitFactor"] >= 1.25,
        "sharpe": result_metrics["sharpe"] >= 1.0,
        "maxDrawdown": result_metrics["maxDrawdownPercent"] <= 12.0,
    }
    result = {
        "phase": args.phase,
        "from": args.from_date,
        "to": args.to_date,
        "algorithmVersion": "quant-v1",
        "costModelVersion": "nse-equity-conservative-v1",
        "configuration": vars(args),
        "metrics": result_metrics,
        "gates": gates,
        "passed": all(gates.values()) if args.phase == "out_of_sample" else None,
        "trades": [asdict(trade) for trade in closed],
    }
    output = Path(args.output).resolve()
    output.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")
    run_cursor = db.cursor()
    run_key = f"{args.phase}-{args.from_date}-{args.to_date}-quant-v1"
    run_cursor.execute(
        """INSERT INTO qualification_run
           (run_key, run_type, algorithm_version, cost_model_version, started_at, completed_at, status, configuration_json, result_json)
           VALUES (%s, %s, 'quant-v1', 'nse-equity-conservative-v1', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), %s, %s, %s)
           ON DUPLICATE KEY UPDATE completed_at=VALUES(completed_at), status=VALUES(status), result_json=VALUES(result_json)""",
        (run_key, args.phase.upper(), "PASSED" if result.get("passed") else "FAILED" if result.get("passed") is False else "MEASURED",
         json.dumps(result["configuration"], default=str), json.dumps({"metrics": result_metrics, "gates": gates})),
    )
    db.commit()
    db.close()
    print(json.dumps({"output": str(output), "metrics": result_metrics, "gates": gates, "passed": result["passed"]}, indent=2))
    return 1 if args.phase == "out_of_sample" and not result["passed"] else 0


if __name__ == "__main__":
    sys.exit(run(arguments()))
