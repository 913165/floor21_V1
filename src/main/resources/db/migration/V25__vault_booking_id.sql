ALTER TABLE vault_entries
    ADD COLUMN booking_id UUID REFERENCES bookings (id);

CREATE INDEX idx_vault_entries_booking_date ON vault_entries (booking_id, entry_date DESC);
