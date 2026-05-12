CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    receipt_date    DATE NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    payment_mode    VARCHAR(50),
    cheque_no       VARCHAR(50),
    bank_name       VARCHAR(100),
    remarks         TEXT,
    created_at      TIMESTAMP DEFAULT now()
);
