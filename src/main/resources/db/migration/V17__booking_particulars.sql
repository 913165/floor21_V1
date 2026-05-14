ALTER TABLE bookings
    ADD COLUMN quoted_amount DECIMAL(15, 2),
    ADD COLUMN brokerage DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN tds DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN gst DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN final_amt DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN due_amount_date DATE,
    ADD COLUMN booking_intimation_date DATE,
    ADD COLUMN noc_request_date DATE,
    ADD COLUMN market_value DECIMAL(15, 2),
    ADD COLUMN stamp_duty_amount DECIMAL(15, 2),
    ADD COLUMN registration_amount DECIMAL(15, 2);
