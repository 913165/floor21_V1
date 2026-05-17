ALTER TABLE builders
    ADD COLUMN expenses_pin_hash VARCHAR(255);

CREATE TABLE builder_expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders (id),
    expense_date    DATE NOT NULL,
    description     VARCHAR(300) NOT NULL,
    category        VARCHAR(100),
    paid_to         VARCHAR(200),
    payment_mode    VARCHAR(50),
    amount          NUMERIC(15, 2) NOT NULL,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT builder_expenses_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_builder_expenses_builder_date ON builder_expenses (builder_id, expense_date DESC);
