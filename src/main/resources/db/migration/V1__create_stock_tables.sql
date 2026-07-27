CREATE TABLE nifty50_constituent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rank_number INT NOT NULL,
    security_id VARCHAR(20) NOT NULL,
    symbol VARCHAR(40) NOT NULL,
    stock_name VARCHAR(150) NOT NULL,
    sector VARCHAR(100) NOT NULL,
    weight_percent DECIMAL(6,3) NOT NULL,
    exchange_segment VARCHAR(20) NOT NULL DEFAULT 'NSE_EQ',
    instrument_type VARCHAR(20) NOT NULL DEFAULT 'EQUITY',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_nifty50_rank UNIQUE (rank_number),
    CONSTRAINT uk_nifty50_security UNIQUE (security_id),
    CONSTRAINT uk_nifty50_symbol UNIQUE (symbol)
);

CREATE TABLE stock_candle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    constituent_id BIGINT NOT NULL,
    interval_start DATETIME NOT NULL,
    open_price DECIMAL(19,4) NOT NULL,
    high_price DECIMAL(19,4) NOT NULL,
    low_price DECIMAL(19,4) NOT NULL,
    close_price DECIMAL(19,4) NOT NULL,
    volume BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_candle_constituent FOREIGN KEY (constituent_id) REFERENCES nifty50_constituent(id),
    CONSTRAINT uk_candle_constituent_time UNIQUE (constituent_id, interval_start),
    INDEX idx_candle_time (interval_start)
);
