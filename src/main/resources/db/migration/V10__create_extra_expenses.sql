CREATE TABLE extra_expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    description     VARCHAR(300),
    amount          DECIMAL(15,2),
    expense_date    DATE,
    created_at      TIMESTAMP DEFAULT now()
);
