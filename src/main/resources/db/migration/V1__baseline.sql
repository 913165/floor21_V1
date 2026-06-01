-- Floor21 consolidated schema + seed (formerly V1–V50).
-- Fresh database only: drop and recreate DB, or use an empty database.

-- -----------------------------------------------------------------------------
-- V1__create_builders.sql
-- -----------------------------------------------------------------------------
CREATE TABLE builders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name    VARCHAR(200) NOT NULL,
    email           VARCHAR(150) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    logo_url        VARCHAR(500),
    address         TEXT,
    city            VARCHAR(100),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V2__create_users.sql
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now(),
    UNIQUE(builder_id, email)
);

-- -----------------------------------------------------------------------------
-- V3__create_buildings.sql
-- -----------------------------------------------------------------------------
CREATE TABLE buildings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id          UUID NOT NULL REFERENCES builders(id),
    building_name       VARCHAR(200) NOT NULL,
    total_floors        INT NOT NULL,
    parking_floors      INT DEFAULT 0,
    flats_per_floor     INT NOT NULL,
    bhk1_per_floor      INT DEFAULT 0,
    bhk2_per_floor      INT DEFAULT 0,
    bhk3_per_floor      INT DEFAULT 0,
    address             TEXT,
    city                VARCHAR(100),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V4__create_flats.sql
-- -----------------------------------------------------------------------------
CREATE TABLE flats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID NOT NULL REFERENCES buildings(id),
    flat_number     VARCHAR(20) NOT NULL,
    floor_number    INT NOT NULL,
    unit_number     INT NOT NULL,
    bhk_type        VARCHAR(10) NOT NULL,
    area_sqft       DECIMAL(10,2),
    base_price      DECIMAL(15,2),
    status          VARCHAR(20) DEFAULT 'AVAILABLE',
    is_parking      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT now(),
    UNIQUE(building_id, flat_number)
);

-- -----------------------------------------------------------------------------
-- V5__create_clients.sql
-- -----------------------------------------------------------------------------
CREATE TABLE clients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id              UUID NOT NULL REFERENCES builders(id),
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100),
    company_name            VARCHAR(200),
    occupation              VARCHAR(100),
    address1                TEXT,
    address2                TEXT,
    address3                TEXT,
    city                    VARCHAR(100),
    phone_office            VARCHAR(20),
    phone_residence         VARCHAR(20),
    mobile1                 VARCHAR(20),
    mobile2                 VARCHAR(20),
    email1                  VARCHAR(150),
    email2                  VARCHAR(150),
    pan_number              VARCHAR(20),
    aadhaar_number          VARCHAR(20),
    dob                     DATE,
    date_of_marriage        DATE,
    dnd_no_call             BOOLEAN DEFAULT FALSE,
    dnd_only_email          BOOLEAN DEFAULT FALSE,
    comm_address1           TEXT,
    comm_address2           TEXT,
    comm_address3           TEXT,
    comm_city               VARCHAR(100),
    name_plate_info         VARCHAR(300),
    particulars             TEXT,
    created_at              TIMESTAMP DEFAULT now(),
    updated_at              TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V6__create_brokers.sql
-- -----------------------------------------------------------------------------
CREATE TABLE brokers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    full_name       VARCHAR(200) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(150),
    commission_pct  DECIMAL(5,2) DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V7__create_bookings.sql
-- -----------------------------------------------------------------------------
CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id          UUID NOT NULL REFERENCES builders(id),
    booking_code        VARCHAR(30) UNIQUE NOT NULL,
    flat_id             UUID NOT NULL REFERENCES flats(id),
    client_id           UUID NOT NULL REFERENCES clients(id),
    broker_id           UUID REFERENCES brokers(id),
    executive_id        UUID REFERENCES users(id),
    file_no             VARCHAR(50),
    booking_date        DATE NOT NULL,
    consideration_amt   DECIMAL(15,2) DEFAULT 0,
    scheme              VARCHAR(100),
    parking_info        TEXT,
    reference           TEXT,
    is_bare_flat        BOOLEAN DEFAULT FALSE,
    particulars         TEXT,
    status              VARCHAR(20) DEFAULT 'ACTIVE',
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V8__create_receipts.sql
-- -----------------------------------------------------------------------------
CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    receipt_date    DATE NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    payment_mode    VARCHAR(50),
    cheque_no       VARCHAR(50),
    bank_name       VARCHAR(100),
    remarks         TEXT,
    created_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V9__create_cancellations.sql
-- -----------------------------------------------------------------------------
CREATE TABLE cancellations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    cancel_date     DATE NOT NULL,
    reason          TEXT,
    refund_amount   DECIMAL(15,2) DEFAULT 0,
    created_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V10__create_extra_expenses.sql
-- -----------------------------------------------------------------------------
CREATE TABLE extra_expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    description     VARCHAR(300),
    amount          DECIMAL(15,2),
    expense_date    DATE,
    created_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V11__create_slabs.sql
-- -----------------------------------------------------------------------------
CREATE TABLE slabs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID REFERENCES buildings(id),
    slab_name       VARCHAR(100),
    description     TEXT,
    rate_per_sqft   DECIMAL(10,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- V12__builders_platform_admin.sql
-- -----------------------------------------------------------------------------
ALTER TABLE builders
    ADD COLUMN IF NOT EXISTS is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- -----------------------------------------------------------------------------
-- V13__seed_data.sql
-- -----------------------------------------------------------------------------
-- Passwords use Spring Security {noop} prefix (DelegatingPasswordEncoder)

INSERT INTO builders (company_name, email, password_hash, city, is_platform_admin, is_active)
VALUES ('Floor21 Platform', 'super@floor21.com', '{noop}super123', 'System', TRUE, TRUE);

INSERT INTO builders (company_name, email, password_hash, city, is_platform_admin, is_active)
VALUES
    ('Skyline Homes Pvt Ltd', 'admin@skylinehomes.com', '{noop}admin123', 'Mumbai', FALSE, TRUE),
    ('Green Valley Developers', 'admin@greenvalley.com', '{noop}admin123', 'Pune', FALSE, TRUE),
    ('Sunrise Realty', 'admin@sunriserealty.com', '{noop}admin123', 'Bangalore', FALSE, TRUE);

INSERT INTO users (builder_id, full_name, email, password_hash, role, is_active)
SELECT b.id, 'Skyline Admin', 'staff.admin@skylinehomes.com', '{noop}staff123', 'BUILDER_ADMIN', TRUE
FROM builders b WHERE b.email = 'admin@skylinehomes.com';

INSERT INTO users (builder_id, full_name, email, password_hash, role, is_active)
SELECT b.id, 'Skyline Executive', 'exec@skylinehomes.com', '{noop}exec123', 'EXECUTIVE', TRUE
FROM builders b WHERE b.email = 'admin@skylinehomes.com';

INSERT INTO buildings (builder_id, building_name, total_floors, parking_floors, flats_per_floor,
                       bhk1_per_floor, bhk2_per_floor, bhk3_per_floor, city, is_active)
SELECT b.id, 'Tower A - Skyline Heights', 20, 2, 6, 0, 3, 3, 'Mumbai', TRUE
FROM builders b WHERE b.email = 'admin@skylinehomes.com';

INSERT INTO clients (builder_id, first_name, last_name, mobile1, email1, city)
SELECT b.id, 'Rahul', 'Verma', '9876500001', 'rahul.verma@example.com', 'Mumbai'
FROM builders b WHERE b.email = 'admin@skylinehomes.com';

INSERT INTO brokers (builder_id, full_name, phone, email, commission_pct, is_active)
SELECT b.id, 'Prime Realty Brokers', '0224000000', 'desk@primerealty.com', 2.5, TRUE
FROM builders b WHERE b.email = 'admin@skylinehomes.com';

-- -----------------------------------------------------------------------------
-- V14__building_floor_plan_paths.sql
-- -----------------------------------------------------------------------------
ALTER TABLE buildings
    ADD COLUMN floor_plan_1bhk VARCHAR(500),
    ADD COLUMN floor_plan_2bhk VARCHAR(500),
    ADD COLUMN floor_plan_3bhk VARCHAR(500);

-- -----------------------------------------------------------------------------
-- V15__payment_slab_templates_and_booking_slabs.sql
-- -----------------------------------------------------------------------------
CREATE TABLE payment_slab_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sort_order        INTEGER        NOT NULL,
    milestone_label   VARCHAR(800)   NOT NULL,
    suggested_percent DECIMAL(9, 4)  NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_slab_templates_sort ON payment_slab_templates (sort_order);
CREATE INDEX idx_payment_slab_templates_active ON payment_slab_templates (active);

CREATE TABLE booking_payment_slabs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID           NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    template_id       UUID           NULL REFERENCES payment_slab_templates (id) ON DELETE SET NULL,
    sort_order        INTEGER        NOT NULL,
    milestone_label   VARCHAR(800)   NOT NULL,
    due_date          DATE           NULL,
    percent           DECIMAL(9, 4)  NULL,
    extra_amount      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    agreed_amount     DECIMAL(15, 2) NULL,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_payment_slabs_booking ON booking_payment_slabs (booking_id);

INSERT INTO payment_slab_templates (sort_order, milestone_label, suggested_percent, active, created_at)
VALUES (1, 'Initial booking amount', 10, true, NOW()),
       (2, 'On or after execution of Agreement', 15, true, NOW());

-- -----------------------------------------------------------------------------
-- V16__payment_slab_templates_builder_id.sql
-- -----------------------------------------------------------------------------
-- Payment milestones are owned by each builder (tenant).

ALTER TABLE payment_slab_templates
    ADD COLUMN builder_id UUID REFERENCES builders (id);

UPDATE payment_slab_templates t
SET builder_id = (SELECT b.id FROM builders b WHERE b.email = 'admin@skylinehomes.com' LIMIT 1)
WHERE t.builder_id IS NULL;

-- Copy Skyline seed milestones to other nonâ€“platform builders so they start with the same defaults.
INSERT INTO payment_slab_templates (id, sort_order, milestone_label, suggested_percent, active, created_at, builder_id)
SELECT gen_random_uuid(),
       p.sort_order,
       p.milestone_label,
       p.suggested_percent,
       p.active,
       NOW(),
       b.id
FROM builders b
CROSS JOIN payment_slab_templates p
WHERE b.is_platform_admin = FALSE
  AND b.id <> (SELECT id FROM builders bb WHERE bb.email = 'admin@skylinehomes.com' LIMIT 1)
  AND p.builder_id = (SELECT id FROM builders bb WHERE bb.email = 'admin@skylinehomes.com' LIMIT 1);

ALTER TABLE payment_slab_templates
    ALTER COLUMN builder_id SET NOT NULL;

CREATE INDEX idx_payment_slab_templates_builder ON payment_slab_templates (builder_id);

-- -----------------------------------------------------------------------------
-- V17__booking_particulars.sql
-- -----------------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN quoted_amount DECIMAL(15, 2),
    ADD COLUMN brokerage DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN tds DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN gst DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN final_amt DECIMAL(15, 2) DEFAULT 0,
    ADD COLUMN due_amount_date DATE,
    ADD COLUMN booking_intimation_date DATE,
    ADD COLUMN noc_request_date DATE,
    ADD COLUMN market_value DECIMAL(15, 2),
    ADD COLUMN stamp_duty_amount DECIMAL(15, 2),
    ADD COLUMN registration_amount DECIMAL(15, 2);

-- -----------------------------------------------------------------------------
-- V18__receipt_entry_columns.sql
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- V19__receipt_serial_per_booking.sql
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- V20__receipt_number_match_serial.sql
-- -----------------------------------------------------------------------------
-- Align display receipt_number with incremental receipt_serial (per booking).
UPDATE receipts
SET receipt_number = CAST(receipt_serial AS VARCHAR(64))
WHERE receipt_serial IS NOT NULL;

-- -----------------------------------------------------------------------------
-- V21__create_banks.sql
-- -----------------------------------------------------------------------------
CREATE TABLE banks (
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    builder_id          UUID        NOT NULL REFERENCES builders (id),
    bank_name           VARCHAR(200) NOT NULL,
    branch              VARCHAR(200),
    ifsc_code           VARCHAR(20),
    account_number      VARCHAR(64),
    account_holder_name VARCHAR(200),
    notes                 TEXT,
    active                BOOLEAN     NOT NULL DEFAULT true,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_banks_builder ON banks (builder_id);

-- -----------------------------------------------------------------------------
-- V22__receipt_deposit_bank.sql
-- -----------------------------------------------------------------------------
ALTER TABLE receipts
    ADD COLUMN deposit_bank_id UUID REFERENCES banks (id);

CREATE INDEX idx_receipts_deposit_bank ON receipts (deposit_bank_id);

-- -----------------------------------------------------------------------------
-- V23__vault.sql
-- -----------------------------------------------------------------------------
ALTER TABLE builders
    ADD COLUMN vault_pin_hash VARCHAR(255);

CREATE TABLE vault_entries (
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    builder_id   UUID        NOT NULL REFERENCES builders (id),
    client_name  VARCHAR(200) NOT NULL,
    amount       NUMERIC(15, 2) NOT NULL,
    entry_date   DATE        NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT vault_entries_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_vault_entries_builder_date ON vault_entries (builder_id, entry_date DESC);

-- -----------------------------------------------------------------------------
-- V24__vault_flat_and_payment_mode.sql
-- -----------------------------------------------------------------------------
ALTER TABLE vault_entries
    ADD COLUMN flat_number VARCHAR(50),
    ADD COLUMN payment_mode VARCHAR(30);

-- -----------------------------------------------------------------------------
-- V25__vault_booking_id.sql
-- -----------------------------------------------------------------------------
ALTER TABLE vault_entries
    ADD COLUMN booking_id UUID REFERENCES bookings (id);

CREATE INDEX idx_vault_entries_booking_date ON vault_entries (booking_id, entry_date DESC);

-- -----------------------------------------------------------------------------
-- V26__vault_payment_slab.sql
-- -----------------------------------------------------------------------------
ALTER TABLE vault_entries
    ADD COLUMN payment_slab_id UUID REFERENCES booking_payment_slabs (id);

CREATE INDEX idx_vault_entries_payment_slab ON vault_entries (payment_slab_id);

-- -----------------------------------------------------------------------------
-- V27__vault_booking_profile.sql
-- -----------------------------------------------------------------------------
CREATE TABLE vault_booking_profiles (
    booking_id           UUID PRIMARY KEY REFERENCES bookings (id) ON DELETE CASCADE,
    builder_id           UUID        NOT NULL REFERENCES builders (id),
    total_consideration  NUMERIC(15, 2),
    register_value       NUMERIC(15, 2),
    extra_amount         NUMERIC(15, 2),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_vault_booking_profiles_builder ON vault_booking_profiles (builder_id);

-- -----------------------------------------------------------------------------
-- V28__builder_expenses_hub.sql
-- -----------------------------------------------------------------------------
ALTER TABLE builders
    ADD COLUMN expenses_pin_hash VARCHAR(255);

CREATE TABLE builder_expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders (id),
    expense_date    DATE NOT NULL,
    description     VARCHAR(300) NOT NULL,
    category        VARCHAR(100),
    paid_to         VARCHAR(200),
    payment_mode    VARCHAR(50),
    amount          NUMERIC(15, 2) NOT NULL,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT builder_expenses_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_builder_expenses_builder_date ON builder_expenses (builder_id, expense_date DESC);

-- -----------------------------------------------------------------------------
-- V29__platform_admin.sql
-- -----------------------------------------------------------------------------
ALTER TABLE builders ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

CREATE TABLE platform_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_email VARCHAR(150) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(64),
    builder_id  UUID REFERENCES builders(id),
    details     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_platform_audit_created ON platform_audit_log (created_at DESC);
CREATE INDEX idx_platform_audit_builder ON platform_audit_log (builder_id);

CREATE TABLE platform_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO platform_settings (setting_key, setting_value) VALUES
    ('default_vault_enabled', 'true'),
    ('default_expenses_enabled', 'true'),
    ('default_receipt_prefix', 'RCP'),
    ('support_email', 'support@floor21.com')
ON CONFLICT (setting_key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- V30__user_building_assignments.sql
-- -----------------------------------------------------------------------------
CREATE TABLE user_building_assignments (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    building_id  UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, building_id)
);

CREATE INDEX idx_user_building_assignments_building ON user_building_assignments(building_id);

-- -----------------------------------------------------------------------------
-- V31__booking_slab_payments.sql
-- -----------------------------------------------------------------------------
CREATE TABLE booking_slab_payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_slab_id UUID           NOT NULL REFERENCES booking_payment_slabs (id) ON DELETE CASCADE,
    payment_date    DATE           NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    reference       VARCHAR(200),
    sort_order      INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_slab_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_booking_slab_payments_slab ON booking_slab_payments (payment_slab_id);
CREATE INDEX idx_booking_slab_payments_date ON booking_slab_payments (payment_slab_id, payment_date);

-- -----------------------------------------------------------------------------
-- V32__payment_slab_templates_building.sql
-- -----------------------------------------------------------------------------
-- Payment milestones are defined per building (Floor21 platform admin).

ALTER TABLE payment_slab_templates
    ADD COLUMN building_id UUID REFERENCES buildings (id);

UPDATE payment_slab_templates t
SET building_id = (
    SELECT b.id
    FROM buildings b
    WHERE b.builder_id = t.builder_id
    ORDER BY lower(b.building_name)
    LIMIT 1
)
WHERE building_id IS NULL;

INSERT INTO payment_slab_templates (id, sort_order, milestone_label, suggested_percent, active, created_at, builder_id, building_id)
SELECT gen_random_uuid(),
       p.sort_order,
       p.milestone_label,
       p.suggested_percent,
       p.active,
       NOW(),
       bld.builder_id,
       bld.id
FROM payment_slab_templates p
         CROSS JOIN buildings bld
         INNER JOIN builders br ON br.id = bld.builder_id
WHERE br.is_platform_admin = FALSE
  AND p.builder_id IN (SELECT id FROM builders WHERE is_platform_admin = TRUE);

DELETE FROM payment_slab_templates p
WHERE p.builder_id IN (SELECT id FROM builders WHERE is_platform_admin = TRUE);

DELETE FROM payment_slab_templates WHERE building_id IS NULL;

ALTER TABLE payment_slab_templates
    ALTER COLUMN building_id SET NOT NULL;

CREATE INDEX idx_payment_slab_templates_building ON payment_slab_templates (building_id);

-- -----------------------------------------------------------------------------
-- V33__receipt_payment_slab.sql
-- -----------------------------------------------------------------------------
-- Receipts are recorded against a booking payment slab (milestone).
ALTER TABLE receipts
    ADD COLUMN payment_slab_id UUID REFERENCES booking_payment_slabs (id);

CREATE INDEX idx_receipts_payment_slab ON receipts (payment_slab_id);

-- -----------------------------------------------------------------------------
-- V34__booking_slab_payments_receipt.sql
-- -----------------------------------------------------------------------------
-- Slab schedule payment rows generated from buyer receipts (waterfall allocation).
ALTER TABLE booking_slab_payments
    ADD COLUMN receipt_id UUID REFERENCES receipts (id) ON DELETE CASCADE;

CREATE INDEX idx_booking_slab_payments_receipt ON booking_slab_payments (receipt_id);

-- -----------------------------------------------------------------------------
-- V35__partner_flat_assignments.sql
-- -----------------------------------------------------------------------------
CREATE TABLE partner_flat_assignments (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    flat_id     UUID NOT NULL REFERENCES flats(id) ON DELETE CASCADE,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, flat_id),
    CONSTRAINT uq_partner_flat_assignments_flat UNIQUE (flat_id)
);

CREATE INDEX idx_partner_flat_assignments_building ON partner_flat_assignments(building_id);
CREATE INDEX idx_partner_flat_assignments_user_building ON partner_flat_assignments(user_id, building_id);

-- -----------------------------------------------------------------------------
-- V36__user_admin_visible_password.sql
-- -----------------------------------------------------------------------------
-- Last password set via platform admin (Users screen); for display when editing only.
ALTER TABLE users ADD COLUMN IF NOT EXISTS admin_visible_password VARCHAR(255);

-- -----------------------------------------------------------------------------
-- V37__vault_access_flags.sql
-- -----------------------------------------------------------------------------
ALTER TABLE builders
    ADD COLUMN vault_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ADD COLUMN vault_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing builder admins keep vault until platform admin turns it off per user.
UPDATE users
SET vault_access_enabled = TRUE
WHERE role = 'BUILDER_ADMIN';

-- -----------------------------------------------------------------------------
-- V38__building_vault_enabled.sql
-- -----------------------------------------------------------------------------
ALTER TABLE buildings
    ADD COLUMN vault_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- -----------------------------------------------------------------------------
-- V39__user_building_vault_access.sql
-- -----------------------------------------------------------------------------
CREATE TABLE user_building_vault_access (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    building_id UUID NOT NULL REFERENCES buildings (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, building_id)
);

-- Migrate prior builder / user / building flags into explicit grants.
INSERT INTO user_building_vault_access (user_id, building_id, enabled)
SELECT u.id, b.id, TRUE
FROM users u
         JOIN buildings b ON b.builder_id = u.builder_id
         JOIN builders br ON br.id = u.builder_id
WHERE u.role = 'BUILDER_ADMIN'
  AND u.vault_access_enabled = TRUE
  AND b.vault_enabled = TRUE
  AND br.vault_enabled = TRUE
ON CONFLICT (user_id, building_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- V40__vault_access_config_only.sql
-- -----------------------------------------------------------------------------
-- Vault access is only via Platform â†’ Vault config (user_building_vault_access).
-- Legacy per-builder / per-building / per-user flags are off by default.

ALTER TABLE builders ALTER COLUMN vault_enabled SET DEFAULT FALSE;
UPDATE builders SET vault_enabled = FALSE;

ALTER TABLE buildings ALTER COLUMN vault_enabled SET DEFAULT FALSE;
UPDATE buildings SET vault_enabled = FALSE;

UPDATE users SET vault_access_enabled = FALSE;

-- Drop auto-grants from V39 backfill; re-add combinations in Vault config as needed.
DELETE FROM user_building_vault_access;

-- -----------------------------------------------------------------------------
-- V41__vault_entry_type.sql
-- -----------------------------------------------------------------------------
ALTER TABLE vault_entries
    ADD COLUMN entry_type VARCHAR(20) NOT NULL DEFAULT 'INCOME';

UPDATE vault_entries SET entry_type = 'INCOME' WHERE entry_type IS NULL;

CREATE INDEX idx_vault_entries_booking_type_date
    ON vault_entries (booking_id, entry_type, entry_date DESC);

-- -----------------------------------------------------------------------------
-- V42__widen_flat_bhk_type.sql
-- -----------------------------------------------------------------------------
ALTER TABLE flats
    ALTER COLUMN bhk_type TYPE VARCHAR(20);

-- -----------------------------------------------------------------------------
-- V43__building_bhk_mix_json.sql
-- -----------------------------------------------------------------------------
ALTER TABLE buildings
    ADD COLUMN bhk_mix_per_floor TEXT;

-- -----------------------------------------------------------------------------
-- V44__flat_duplex_links.sql
-- -----------------------------------------------------------------------------
ALTER TABLE flats
    ADD COLUMN duplex_primary_flat_id UUID REFERENCES flats (id),
    ADD COLUMN duplex_secondary_flat_id UUID REFERENCES flats (id);

CREATE INDEX idx_flats_duplex_primary ON flats (duplex_primary_flat_id)
    WHERE duplex_primary_flat_id IS NOT NULL;

CREATE INDEX idx_flats_duplex_secondary ON flats (duplex_secondary_flat_id)
    WHERE duplex_secondary_flat_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- V45__flat_floor_merge_restore.sql
-- -----------------------------------------------------------------------------
ALTER TABLE flats
    ADD COLUMN merged_into_flat_id UUID REFERENCES flats(id),
    ADD COLUMN merged_absorbed_flat_id UUID REFERENCES flats(id),
    ADD COLUMN pre_merge_bhk_type VARCHAR(20),
    ADD COLUMN pre_merge_area_sqft DECIMAL(10, 2),
    ADD COLUMN pre_merge_base_price DECIMAL(15, 2),
    ADD COLUMN pre_merge_status VARCHAR(20);

CREATE INDEX idx_flats_merged_into ON flats (merged_into_flat_id)
    WHERE merged_into_flat_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- V46__user_tax_contact_details.sql
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN pan_number VARCHAR(20),
    ADD COLUMN tan_number VARCHAR(20),
    ADD COLUMN gst_number VARCHAR(20),
    ADD COLUMN mobile_number VARCHAR(20),
    ADD COLUMN address TEXT;

-- -----------------------------------------------------------------------------
-- V47__user_address_state_pin.sql
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN address_state VARCHAR(100),
    ADD COLUMN address_pin VARCHAR(6);

-- -----------------------------------------------------------------------------
-- V48__builders_optional_login.sql
-- -----------------------------------------------------------------------------
-- Projects (builders) no longer require a login on the tenant row; users are added separately.
ALTER TABLE builders ALTER COLUMN email DROP NOT NULL;
ALTER TABLE builders ALTER COLUMN password_hash DROP NOT NULL;

-- -----------------------------------------------------------------------------
-- V49__users_optional_builder.sql
-- -----------------------------------------------------------------------------
-- Platform users can exist before they are linked to a project (via Partners).
ALTER TABLE users ALTER COLUMN builder_id DROP NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_builder_id_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email));

-- -----------------------------------------------------------------------------
-- V50__user_project_assignments.sql
-- -----------------------------------------------------------------------------
-- Users can belong to multiple projects (builders).
CREATE TABLE user_project_assignments (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    builder_id UUID NOT NULL REFERENCES builders(id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, builder_id)
);

CREATE INDEX idx_user_project_assignments_builder ON user_project_assignments(builder_id);

INSERT INTO user_project_assignments (user_id, builder_id, role)
SELECT id, builder_id, role
FROM users
WHERE builder_id IS NOT NULL;

UPDATE users SET builder_id = NULL WHERE builder_id IS NOT NULL;

