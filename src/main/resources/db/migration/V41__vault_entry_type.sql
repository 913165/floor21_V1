ALTER TABLE vault_entries
    ADD COLUMN entry_type VARCHAR(20) NOT NULL DEFAULT 'INCOME';

UPDATE vault_entries SET entry_type = 'INCOME' WHERE entry_type IS NULL;

CREATE INDEX idx_vault_entries_booking_type_date
    ON vault_entries (booking_id, entry_type, entry_date DESC);
