ALTER TABLE vault_entries
    ADD COLUMN payment_slab_id UUID REFERENCES booking_payment_slabs (id);

CREATE INDEX idx_vault_entries_payment_slab ON vault_entries (payment_slab_id);
