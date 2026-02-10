-- Trade Journal Database Schema for Oracle Autonomous Database

-- Users table
CREATE TABLE users (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE,
    user_secret VARCHAR2(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_users_email ON users(email);

-- Broker Accounts table
CREATE TABLE broker_accounts (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    user_id RAW(16) NOT NULL,
    broker_id NUMBER(10),
    account_number VARCHAR2(100),
    account_name VARCHAR2(255),
    is_manual NUMBER(1) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_broker_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_broker_accounts_user_id ON broker_accounts(user_id);

-- Trades table
CREATE TABLE trades (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    account_id RAW(16) NOT NULL,
    symbol VARCHAR2(50) NOT NULL,
    trade_date DATE NOT NULL,
    action VARCHAR2(50) NOT NULL,
    quantity NUMBER(19,8) NOT NULL,
    price NUMBER(19,8) NOT NULL,
    commission NUMBER(19,8) DEFAULT 0,
    fees NUMBER(19,8) DEFAULT 0,
    strategy_group_id RAW(16),
    notes VARCHAR2(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    realized_pl NUMBER(19,8),
    trade_hash VARCHAR2(255),
    CONSTRAINT fk_trades_account FOREIGN KEY (account_id) REFERENCES broker_accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_trades_account_id ON trades(account_id);
CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_trade_date ON trades(trade_date);
CREATE INDEX idx_trades_trade_hash ON trades(trade_hash);

-- Comments for documentation
COMMENT ON TABLE users IS 'User accounts for the trade journal application';
COMMENT ON TABLE broker_accounts IS 'Brokerage accounts linked to users';
COMMENT ON TABLE trades IS 'Individual trade records';

COMMIT;
