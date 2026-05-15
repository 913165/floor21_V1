-- Align display receipt_number with incremental receipt_serial (per booking).
UPDATE receipts
SET receipt_number = CAST(receipt_serial AS VARCHAR(64))
WHERE receipt_serial IS NOT NULL;
