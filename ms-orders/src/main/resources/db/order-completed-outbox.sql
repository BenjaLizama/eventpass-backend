CREATE TABLE IF NOT EXISTS order_completed_outbox (
    order_id uuid PRIMARY KEY,
    payload text NOT NULL,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_completed_outbox_next_attempt
    ON order_completed_outbox (next_attempt_at, order_id);
