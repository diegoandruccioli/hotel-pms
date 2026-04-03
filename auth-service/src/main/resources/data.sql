-- Seed default ADMIN user for local development
INSERT INTO user_account (id, username, password_hash, email, role, active, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'admin',
    '$2a$10$8aXe/PIDoC/tOecWVAMxsu57InT1n4F4Uq2ObRGB4W8DhGowDrbMi',
    'admin@hotel.com',
    'ADMIN',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash;
