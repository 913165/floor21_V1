CREATE TABLE booking_slab_payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_slab_id UUID           NOT NULL REFERENCES booking_payment_slabs (id) ON DELETE CASCADE,
    payment_date    DATE           NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    reference       VARCHAR(200),
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_slab_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_booking_slab_payments_slab ON booking_slab_payments (payment_slab_id);
CREATE INDEX idx_booking_slab_payments_date ON booking_slab_payments (payment_slab_id, payment_date);
