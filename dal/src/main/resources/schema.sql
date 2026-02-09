-- Trade Journal Database Schema

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    user_secret VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Brokers Table (e.g., Fidelity, IBKR, Webull)
CREATE TABLE IF NOT EXISTS brokers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    api_enabled BOOLEAN DEFAULT FALSE
);

-- Broker Accounts (Linked to a User and a Broker)
CREATE TABLE IF NOT EXISTS broker_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    broker_id INT REFERENCES brokers(id),
    account_number VARCHAR(100), -- Encrypted or masked in real app
    account_name VARCHAR(100),
    is_manual BOOLEAN DEFAULT FALSE, -- TRUE for manual CSV imports
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, broker_id, account_number)
);

-- Trades Table
CREATE TABLE IF NOT EXISTS trades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES broker_accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    action VARCHAR(20) NOT NULL, -- BUY, SELL, BUY_TO_OPEN, SELL_TO_CLOSE, etc.
    quantity DECIMAL(20, 8) NOT NULL, -- Support fractional shares
    price DECIMAL(20, 8) NOT NULL,
    commission DECIMAL(10, 2) DEFAULT 0.00,
    fees DECIMAL(10, 2) DEFAULT 0.00,
    strategy_group_id UUID, -- Link to strategy group (for P&L tracking)
    realized_pl DECIMAL(20, 8), -- Realized P&L for closed trades
    notes TEXT,
    trade_hash VARCHAR(64) UNIQUE, -- Hash of trade properties for deduplication
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Holdings (Current Portfolio State)
CREATE TABLE IF NOT EXISTS holdings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES broker_accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(15, 6) NOT NULL,
    average_cost DECIMAL(15, 6),
    current_price DECIMAL(15, 6), -- Last fetched price
    market_value DECIMAL(15, 6),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, symbol)
);

-- Dividends Table
CREATE TABLE IF NOT EXISTS dividends (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES broker_accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    amount DECIMAL(15, 6) NOT NULL,
    pay_date DATE NOT NULL,
    ex_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, symbol, pay_date)
);

-- Market Data (Historical Prices for Charts/P&L)
CREATE TABLE IF NOT EXISTS market_data (
    symbol VARCHAR(20) NOT NULL,
    price_date DATE NOT NULL,
    close_price DECIMAL(15, 6) NOT NULL,
    source VARCHAR(50), -- e.g., Polygon, AlphaVantage
    PRIMARY KEY (symbol, price_date)
);

-- Pre-fill supported brokers
INSERT INTO brokers (name, api_enabled) VALUES 
('Fidelity', FALSE),
('Interactive Brokers', TRUE),
('Robinhood', TRUE),
('Charles Schwab', FALSE),
('Vanguard', FALSE),
('SnapTrade', TRUE)
ON CONFLICT (name) DO NOTHING;
