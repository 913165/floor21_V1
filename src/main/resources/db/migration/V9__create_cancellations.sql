CREATE TABLE cancellations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    cancel_date     DATE NOT NULL,
    reason          TEXT,
    refund_amount   DECIMAL(15,2) DEFAULT 0,
    created_at      TIMESTAMP DEFAULT now()
);
