# Reliability qualification runbook

The application is a paper-trading research system. It does not place real broker orders and a passing historical result does not guarantee future returns.

## Release gates

The platform gate is evaluated during NSE market hours:

- availability at least 99.5%;
- REST p95 below 500 ms;
- analytics and WebSocket data no more than three seconds old;
- at least 99.5% expected constituent candles;
- no unbounded memory, queue, thread, session, or connection growth;
- no unexplained container restart or unrecovered dependency failure.

The locked out-of-sample strategy gate requires at least 300 trades, positive net expectancy after costs, profit factor at least 1.25, Sharpe at least 1.0, and maximum drawdown no greater than 12%.

## Credential rotation and access

Before deploying this branch:

1. Revoke the previously shared Cloudflare API/tunnel token and create a least-privilege replacement.
2. Generate a new Dhan access token and save it through the protected Settings page.
3. Rotate the MySQL application and root passwords, settings administrator password, and AES-GCM settings key through a coordinated backup-and-redeploy procedure.
4. In Cloudflare Zero Trust, create a self-hosted application for `nse.revvlabs.tech/*`, allow only approved owner/team identities, set a short session duration, and confirm WebSocket traffic passes through Access.
5. Leave port 8080 restricted by the VM firewall to the private network; only Cloudflare Tunnel should provide public ingress.

Do not rotate the AES-GCM key until encrypted settings have been decrypted with the old key and re-saved with the new one. Otherwise the stored credentials become unreadable.

## Isolated two-year data set

Create independent secrets and start the qualification stack:

```bash
cp .qualification.env.example .qualification.env
chmod 600 .qualification.env
docker compose --env-file .qualification.env -f docker-compose.qualification.yml up -d --build
docker compose --env-file .qualification.env -f docker-compose.qualification.yml logs -f qualification-app
```

The app requests Dhan data in 89-day windows and writes only to the qualification MySQL volume. The live Dhan WebSocket is disabled in this stack.

Run the chronological phases after backfill completes:

```bash
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=13306 MYSQL_DATABASE=qualification MYSQL_USER=qualification_user
export MYSQL_PASSWORD='value-from-qualification-env'
python3 scripts/backtest_qualification.py --phase development --from 2024-07-28 --to 2025-07-28 --output development.json
python3 scripts/backtest_qualification.py --phase validation --from 2025-07-28 --to 2026-01-28 --output validation.json
python3 scripts/backtest_qualification.py --phase out_of_sample --from 2026-01-28 --to 2026-07-28 --output out-of-sample.json
```

The out-of-sample command exits nonzero if any financial gate fails. Do not change the algorithm or cost model after viewing that result; a change requires a fresh chronological qualification.

## Load and soak tests

Run k6 against the isolated stack. The default profile ramps to 10 users, holds for one hour, bursts to 20, and maintains 10 WebSockets:

```bash
docker run --rm --network host -i grafana/k6:latest run - < load/k6-qualification.js
```

For a short smoke run:

```bash
docker run --rm --network host -e RAMP_DURATION=30s -e HOLD_DURATION=2m -e BURST_DURATION=30s -e WS_DURATION=3m -i grafana/k6:latest run - < load/k6-qualification.js
```

An eight-hour soak uses `HOLD_DURATION=8h`, `WS_DURATION=8h`, and a 10-user target. Observe Grafana, container restarts, JVM heap after full GC, database connections, scheduler latency, and WebSocket age throughout the run.

## Monitoring and Telegram alerts

Create `.monitoring.env` and `.mysql-exporter.cnf` from their examples, set permissions to `600`, then start the monitoring profile:

```bash
cp .monitoring.env.example .monitoring.env
cp .mysql-exporter.cnf.example .mysql-exporter.cnf
chmod 600 .monitoring.env .mysql-exporter.cnf
docker compose --profile monitoring up -d
```

Grafana listens only on `127.0.0.1:3000`; use an SSH tunnel for access. Trigger and resolve a test alert before relying on Telegram delivery.

## Backup and restore drill

```bash
scripts/backup_mysql.sh
scripts/verify_restore.sh /absolute/path/to/the/generated.sql.gz
```

The restore drill uses the fixed temporary database `trade_restore_verification`, verifies at least eight tables, and removes that database after success. Production data is not overwritten.

## Twenty-day live proof

After historical and technical gates pass, deploy the candidate with paper trading enabled. For 20 NSE trading days, retain Prometheus data and reconcile daily:

- Dhan packet and candle gaps;
- every signal, fill, exit, cost, and risk rejection;
- application incidents and error-budget consumption;
- paper profit factor, drawdown, and rule adherence.

The candidate becomes `QUALIFIED_PAPER` only if the operational gates pass, live profit factor is at least 1.0, drawdown remains below 12%, and at least 30 trades close. Extend beyond 20 days if fewer than 30 trades close.
