# Nifty 50 quantitative engine

## What is exact and what is estimated

Nifty 50 is a free-float market-capitalisation weighted index. NSE's published formula is:

```text
Free-float market cap_i = shares outstanding_i × IWF_i × price_i
Index value = current aggregate free-float market cap / base market cap × base index value
```

The application does not hold shares outstanding, IWF, divisor, or corporate-action files. It uses
the supplied official constituent weights as start-of-period portfolio weights. Therefore:

```text
stock return_i          = live price_i / previous close_i - 1
normalized weight_i     = supplied weight_i / sum(all supplied weights)
return contribution_i   = normalized weight_i × stock return_i
estimated Nifty points_i = previous Nifty close × return contribution_i
synthetic Nifty level   = previous Nifty close × (1 + sum(return contributions))
```

This is exact for a portfolio whose weights match the stored start-of-day weights. It is an
estimate of the exchange index because official weights drift with price, and NSE can adjust its
divisor for corporate actions. The dashboard displays the difference between Dhan's official index
level and the reconstructed basket as `tracking difference` instead of hiding it.

## Impact measures

- **Signed contribution points:** direction and estimated Nifty points caused by one stock.
- **Gross impact:** sum of absolute constituent contributions. It measures internal movement even
  when positive and negative stocks cancel each other.
- **Impact share:** absolute stock contribution divided by gross impact.
- **Top-five concentration:** impact share owned by the five strongest movers.
- **Breadth score:** `(advancers - decliners) / covered constituents`, bounded to `[-1, 1]`.
- **Sector contribution:** sum of constituent point contributions grouped by sector.

## Fifteen-minute statistical nowcast

The forecast is deliberately separate from exact attribution. For each constituent, the engine
calculates 5, 15, and 60-minute returns and aggregates them with normalized index weights.

```text
projected momentum = 0.50 × weighted R5 × 3
                   + 0.30 × weighted R15
                   + 0.20 × weighted R60 / 4

breadth adjustment = breadth score × min(6 bps, 0.12 × dispersion)
forecast return    = clamp(0.65 × projected momentum + breadth adjustment, -1.5%, +1.5%)
expected points    = official Nifty level × forecast return
```

The 5 and 60-minute observations are converted to a common 15-minute horizon. The final shrinkage
factor reduces trend extrapolation. Cross-sectional dispersion supplies the prediction range:

```text
dispersion = sqrt(sum(weight_i × (R5_i - weighted R5)^2))
range      = index level × max(8 bps, 0.55 × sqrt(3) × dispersion)
```

Confidence increases when constituent momentum signs agree and breadth is decisive, and decreases
when cross-sectional dispersion is noisy. Confidence is capped at 85%. This is a transparent
heuristic nowcast, not a trained return guarantee or trading recommendation. A production research
process should walk-forward test coefficients, include transaction costs, prevent look-ahead bias,
and recalibrate after every index rebalance.

## Data flow

```text
Dhan stock WebSocket -> one-minute MySQL candles
Dhan Nifty snapshot  -> official index level and OHLC
MySQL + weights      -> quantitative engine every second
quantitative engine  -> /ws/analytics
browser WebSocket    -> chart, breadth, stock impact, sectors, forecast
```

Official methodology references:

- https://www.niftyindices.com/Methodology/Nifty_Broad_Market_Indices_Methodology.pdf
- https://www.nseindia.com/static/products-services/indices-nifty50-index
