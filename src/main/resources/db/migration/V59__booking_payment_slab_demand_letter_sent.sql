ALTER TABLE booking_payment_slabs
    ADD COLUMN demand_letter_sent_to_client BOOLEAN NOT NULL DEFAULT FALSE;
