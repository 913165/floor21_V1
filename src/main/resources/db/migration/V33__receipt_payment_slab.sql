-- Receipts are recorded against a booking payment slab (milestone).
ALTER TABLE receipts
    ADD COLUMN payment_slab_id UUID REFERENCES booking_payment_slabs (id);

CREATE INDEX idx_receipts_payment_slab ON receipts (payment_slab_id);
