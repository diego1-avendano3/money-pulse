CREATE TABLE transactions (
    id           UUID PRIMARY KEY,
    account_id   TEXT NOT NULL,
    amount       NUMERIC NOT NULL,
    currency     VARCHAR(3) NOT NULL,
    merchant     TEXT NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    persisted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_received_at ON transactions (received_at DESC);
