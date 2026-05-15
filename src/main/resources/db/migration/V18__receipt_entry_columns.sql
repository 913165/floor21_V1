ALTER TABLE receipts
    ADD COLUMN receipt_number VARCHAR(64),
    ADD COLUMN cheque_date DATE,
    ADD COLUMN amount_consideration DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_extra_charges DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_interest_agreement DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_interest_gst DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_tds DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_gst_component DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN deposit_account VARCHAR(200),
    ADD COLUMN dishonoured BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN entered_by_display VARCHAR(200);

UPDATE receipts
SET amount_consideration = amount
WHERE amount IS NOT NULL;
