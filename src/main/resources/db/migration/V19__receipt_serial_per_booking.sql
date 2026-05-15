ALTER TABLE receipts
    ADD COLUMN receipt_serial INTEGER;

UPDATE receipts r
SET receipt_serial = s.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY booking_id ORDER BY created_at NULLS LAST, id) AS rn
    FROM receipts
) s
WHERE r.id = s.id;

ALTER TABLE receipts
    ALTER COLUMN receipt_serial SET NOT NULL;

ALTER TABLE receipts
    ADD CONSTRAINT receipts_booking_serial_uq UNIQUE (booking_id, receipt_serial);
