ALTER TABLE receipts
    ADD COLUMN deposit_bank_id UUID REFERENCES banks (id);

CREATE INDEX idx_receipts_deposit_bank ON receipts (deposit_bank_id);
