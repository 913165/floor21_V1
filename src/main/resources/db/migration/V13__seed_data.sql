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
