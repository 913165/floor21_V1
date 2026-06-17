CREATE TABLE booking_owners (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    client_id           UUID NOT NULL REFERENCES clients (id),
    sort_order          INT NOT NULL DEFAULT 0,
    role                VARCHAR(20) NOT NULL DEFAULT 'CO_OWNER',
    ownership_percent   DECIMAL(5, 2),
    created_at          TIMESTAMPTZ,
    CONSTRAINT uq_booking_owners_booking_client UNIQUE (booking_id, client_id)
);

CREATE INDEX idx_booking_owners_booking ON booking_owners (booking_id);
CREATE INDEX idx_booking_owners_client ON booking_owners (client_id);

INSERT INTO booking_owners (booking_id, client_id, sort_order, role, created_at)
SELECT id, client_id, 0, 'PRIMARY', COALESCE(created_at, NOW())
FROM bookings;

ALTER TABLE receipts
    ADD COLUMN paid_by_client_id UUID REFERENCES clients (id);
