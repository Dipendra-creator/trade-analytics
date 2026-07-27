# Nifty 50 market data

The application stores the supplied Nifty 50 constituents and weights in MySQL, one-minute OHLCV
candles in `stock_candle`, and the most recent quote for each stock in Redis. DhanHQ historical
data is idempotently backfilled from 1 July 2026. During NSE market hours (09:15-15:30 IST,
Monday-Friday), the application subscribes to Dhan's quote WebSocket and continuously updates the
current one-minute candle and Redis latest-quote key.

## Start dependencies

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis
```

Set `DHAN_CLIENT_ID` and `DHAN_ACCESS_TOKEN` in the environment before starting Spring Boot. The
application still starts without them, but logs that ingestion is skipped. Do not store a token in
`application.properties`; Dhan access tokens must be rotated according to your account settings.

```powershell
$env:DHAN_CLIENT_ID='your-client-id'
$env:DHAN_ACCESS_TOKEN='your-access-token'
.\mvnw.cmd spring-boot:run
```

Optional settings:

- `DHAN_BACKFILL_FROM` defaults to `2026-07-01T09:15:00`.
- `DHAN_BACKFILL_TO` defaults to the current time.
- `DHAN_BACKFILL_ENABLED` and `DHAN_LIVE_FEED_ENABLED` independently enable each ingestion path.
- `REDIS_HOST` and `REDIS_PORT` default to `localhost:6379`.

Flyway creates and seeds the tables on startup. It baselines an existing non-empty schema, so the
pre-existing `users` table is not recreated or modified.

## Read API

- `GET /api/nifty50` - all constituents in rank order.
- `GET /api/nifty50/HDFCBANK/latest` - the latest Redis quote.
- `GET /api/nifty50/HDFCBANK/candles?from=2026-07-01T09:15:00&to=2026-07-27T15:30:00`
  - persisted one-minute candles.

The live feed intentionally rolls ticks into one-minute candles instead of inserting an unbounded
row for every exchange tick. Redis is best-effort: a Redis outage does not stop MySQL ingestion.
