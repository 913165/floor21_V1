-- Align flat status with active bookings before enforcing one-active-booking rule.
UPDATE flats f
SET status = 'BOOKED'
WHERE status <> 'BOOKED'
  AND EXISTS (
      SELECT 1 FROM bookings b
      WHERE b.flat_id = f.id AND b.status = 'ACTIVE'
  );

CREATE UNIQUE INDEX idx_bookings_one_active_per_flat
    ON bookings (flat_id)
    WHERE status = 'ACTIVE';
