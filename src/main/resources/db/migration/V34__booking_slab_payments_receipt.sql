-- Slab schedule payment rows generated from buyer receipts (waterfall allocation).
ALTER TABLE booking_slab_payments
    ADD COLUMN receipt_id UUID REFERENCES receipts (id) ON DELETE CASCADE;

CREATE INDEX idx_booking_slab_payments_receipt ON booking_slab_payments (receipt_id);
