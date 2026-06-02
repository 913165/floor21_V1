-- Remove initial demo tenants and related rows. Keeps platform super admin (super@floor21.com) only.
-- Safe to re-run: deletes only known seed projects/users if they still exist.

CREATE TEMP TABLE _demo_builders ON COMMIT DROP AS
SELECT id
FROM builders
WHERE is_platform_admin = FALSE
  AND (
      lower(email) IN (
          'admin@skylinehomes.com',
          'admin@greenvalley.com',
          'admin@sunriserealty.com'
      )
      OR company_name IN (
          'Skyline Homes Pvt Ltd',
          'Green Valley Developers',
          'Sunrise Realty'
      )
  );

CREATE TEMP TABLE _demo_buildings ON COMMIT DROP AS
SELECT id
FROM buildings
WHERE builder_id IN (SELECT id FROM _demo_builders);

CREATE TEMP TABLE _demo_flats ON COMMIT DROP AS
SELECT id
FROM flats
WHERE builder_id IN (SELECT id FROM _demo_builders);

CREATE TEMP TABLE _demo_bookings ON COMMIT DROP AS
SELECT id
FROM bookings
WHERE builder_id IN (SELECT id FROM _demo_builders);

CREATE TEMP TABLE _demo_users ON COMMIT DROP AS
SELECT id
FROM users
WHERE lower(email) IN ('staff.admin@skylinehomes.com', 'exec@skylinehomes.com');

DELETE FROM booking_slab_payments
WHERE payment_slab_id IN (
    SELECT bps.id
    FROM booking_payment_slabs bps
    WHERE bps.booking_id IN (SELECT id FROM _demo_bookings)
);

DELETE FROM booking_slab_payments
WHERE receipt_id IN (
    SELECT r.id FROM receipts r WHERE r.booking_id IN (SELECT id FROM _demo_bookings)
);

DELETE FROM receipts
WHERE booking_id IN (SELECT id FROM _demo_bookings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM booking_payment_slabs
WHERE booking_id IN (SELECT id FROM _demo_bookings);

DELETE FROM vault_booking_profiles
WHERE booking_id IN (SELECT id FROM _demo_bookings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM cancellations
WHERE booking_id IN (SELECT id FROM _demo_bookings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM extra_expenses
WHERE booking_id IN (SELECT id FROM _demo_bookings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM bookings
WHERE id IN (SELECT id FROM _demo_bookings);

DELETE FROM partner_flat_assignments
WHERE building_id IN (SELECT id FROM _demo_buildings)
   OR flat_id IN (SELECT id FROM _demo_flats)
   OR user_id IN (SELECT id FROM _demo_users);

DELETE FROM user_building_vault_access
WHERE building_id IN (SELECT id FROM _demo_buildings)
   OR user_id IN (SELECT id FROM _demo_users);

DELETE FROM user_building_assignments
WHERE building_id IN (SELECT id FROM _demo_buildings)
   OR user_id IN (SELECT id FROM _demo_users);

DELETE FROM user_project_assignments
WHERE builder_id IN (SELECT id FROM _demo_builders)
   OR user_id IN (SELECT id FROM _demo_users);

DELETE FROM payment_slab_templates
WHERE building_id IN (SELECT id FROM _demo_buildings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM slabs
WHERE building_id IN (SELECT id FROM _demo_buildings)
   OR builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM flats
WHERE id IN (SELECT id FROM _demo_flats);

DELETE FROM vault_entries
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM builder_expenses
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM banks
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM buildings
WHERE id IN (SELECT id FROM _demo_buildings);

DELETE FROM clients
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM brokers
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM users
WHERE id IN (SELECT id FROM _demo_users);

DELETE FROM platform_audit_log
WHERE builder_id IN (SELECT id FROM _demo_builders);

DELETE FROM builders
WHERE id IN (SELECT id FROM _demo_builders);
