CREATE TABLE vault_booking_profiles (
    booking_id           UUID PRIMARY KEY REFERENCES bookings (id) ON DELETE CASCADE,
    builder_id           UUID        NOT NULL REFERENCES builders (id),
    total_consideration  NUMERIC(15, 2),
    register_value       NUMERIC(15, 2),
    extra_amount         NUMERIC(15, 2),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_vault_booking_profiles_builder ON vault_booking_profiles (builder_id);
