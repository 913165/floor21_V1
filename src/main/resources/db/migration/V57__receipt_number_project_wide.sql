-- Receipt numbers and serials are unique per project (builder), not per booking/client.

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_booking_serial_uq;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY builder_id
               ORDER BY receipt_date ASC NULLS LAST, created_at ASC NULLS LAST, id ASC
           ) AS rn
    FROM receipts
)
UPDATE receipts r
SET receipt_serial = ranked.rn,
    receipt_number = CAST(ranked.rn AS VARCHAR(64))
FROM ranked
WHERE r.id = ranked.id;

ALTER TABLE receipts
    ADD CONSTRAINT receipts_builder_serial_uq UNIQUE (builder_id, receipt_serial);

CREATE UNIQUE INDEX IF NOT EXISTS receipts_builder_receipt_number_uq
    ON receipts (builder_id, receipt_number)
    WHERE receipt_number IS NOT NULL AND btrim(receipt_number) <> '';
