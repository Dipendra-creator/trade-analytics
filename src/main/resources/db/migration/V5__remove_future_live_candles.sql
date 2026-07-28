-- Earlier live-feed timestamps treated Dhan's India market wall clock as UTC,
-- placing candles 5 hours 30 minutes in the future. Historical rows are untouched.
DELETE FROM stock_candle
WHERE source = 'DHAN_LIVE'
  AND interval_start > CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+05:30') + INTERVAL 60 MINUTE;
