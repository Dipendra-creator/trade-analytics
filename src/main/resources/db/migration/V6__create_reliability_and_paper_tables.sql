CREATE TABLE qualification_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(80) NOT NULL,
    run_type VARCHAR(30) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    cost_model_version VARCHAR(40) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    status VARCHAR(24) NOT NULL,
    configuration_json JSON NOT NULL,
    result_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_qualification_run_key (run_key)
);

CREATE TABLE paper_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signal_key VARCHAR(160) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    symbol VARCHAR(24) NOT NULL,
    side VARCHAR(8) NOT NULL,
    state VARCHAR(20) NOT NULL,
    signal_at TIMESTAMP(6) NOT NULL,
    opened_at TIMESTAMP(6) NOT NULL,
    closed_at TIMESTAMP(6) NULL,
    entry_price DECIMAL(18,4) NOT NULL,
    stop_price DECIMAL(18,4) NOT NULL,
    target_price DECIMAL(18,4) NOT NULL,
    exit_price DECIMAL(18,4) NULL,
    quantity INT NOT NULL,
    confidence DECIMAL(8,4) NOT NULL,
    score DECIMAL(18,6) NOT NULL,
    gross_pnl DECIMAL(18,4) NULL,
    costs DECIMAL(18,4) NULL,
    net_pnl DECIMAL(18,4) NULL,
    exit_reason VARCHAR(24) NULL,
    evidence_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_trade_signal_key (signal_key),
    KEY ix_paper_trade_state_opened (state, opened_at),
    KEY ix_paper_trade_symbol_opened (symbol, opened_at)
);

CREATE TABLE reliability_incident (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_key VARCHAR(100) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    component VARCHAR(40) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    opened_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    details_json JSON NULL,
    PRIMARY KEY (id),
    KEY ix_reliability_incident_open (resolved_at, opened_at)
);

CREATE TABLE paper_daily_result (
    trading_date DATE NOT NULL,
    starting_equity DECIMAL(18,4) NOT NULL,
    ending_equity DECIMAL(18,4) NOT NULL,
    realized_pnl DECIMAL(18,4) NOT NULL,
    trade_count INT NOT NULL,
    wins INT NOT NULL,
    losses INT NOT NULL,
    max_drawdown_percent DECIMAL(8,4) NOT NULL,
    risk_halted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (trading_date)
);
