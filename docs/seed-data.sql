-- ==========================================================
-- Enterprise Hotel PMS - Seed Data Script
-- ==========================================================
-- This script contains sample data that can be injected into the
-- PostgreSQL databases to provide a prepopulated ecosystem 
-- for the demonstration. 
-- 
-- Note: UUIDs are hardcoded to allow relational linking across tables.
-- ==========================================================

-- ----------------------------------------------------------
-- 1. F&B SERVICE SEED DATA (Restaurant Items)
-- Execute this against the `hotel_fb` database (if separated)
-- or the unified schema.
-- ----------------------------------------------------------

-- Assuming a hypothetical table named 'menu_items' for the demo catalog.
-- Check your JPA auto-generated schema to ensure exact table names exist 
-- if you plan to inject this directly.

CREATE TABLE IF NOT EXISTS menu_item (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    price DECIMAL(10, 2),
    available BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO menu_item (id, name, description, category, price, available) VALUES
('11111111-1111-1111-1111-111111111111', 'Classic Beef Burger', 'Brioche bun, cheddar, lettuce, tomato, special sauce', 'FOOD', 15.00, true),
('22222222-2222-2222-2222-222222222222', 'Club Sandwich', 'Turkey, bacon, lettuce, tomato on sourdough', 'FOOD', 12.50, true),
('33333333-3333-3333-3333-333333333333', 'Truffle Fries', 'Shoestring fries, parmesan, truffle oil', 'FOOD', 8.00, true),
('44444444-4444-4444-4444-444444444444', 'Caesar Salad', 'Romaine, croutons, parmesan, house dressing', 'FOOD', 10.00, true),
('55555555-5555-5555-5555-555555555555', 'Local Draft IPA', '16oz rotating local craft beer', 'BEVERAGE', 8.00, true),
('66666666-6666-6666-6666-666666666666', 'House Cabernet', 'Glass of California Cabernet Sauvignon', 'BEVERAGE', 12.00, true),
('77777777-7777-7777-7777-777777777777', 'Fresh Lemonade', 'House-made sparkling lemonade', 'BEVERAGE', 5.00, true)
ON CONFLICT (id) DO NOTHING;


-- ----------------------------------------------------------
-- 2. INVENTORY SERVICE SEED DATA (Rooms)
-- ----------------------------------------------------------

CREATE TABLE IF NOT EXISTS room_type (
    id UUID PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    base_price DECIMAL(10, 2),
    capacity INT
);

INSERT INTO room_type (id, code, name, description, base_price, capacity) VALUES
('88888888-8888-8888-8888-888888888888', 'STD', 'Standard Room', 'Standard queen bed, city view', 120.00, 2),
('99999999-9999-9999-9999-999999999999', 'DLX', 'Deluxe Room', 'King bed, ocean view, balcony', 180.00, 2),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'STE', 'Executive Suite', 'Separate living area, king bed, premium amenities', 350.00, 4)
ON CONFLICT (id) DO NOTHING;


CREATE TABLE IF NOT EXISTS room (
    id UUID PRIMARY KEY,
    room_number VARCHAR(10) UNIQUE NOT NULL,
    room_type_id UUID REFERENCES room_type(id),
    status VARCHAR(50) NOT NULL
);

INSERT INTO room (id, room_number, room_type_id, status) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '101', '88888888-8888-8888-8888-888888888888', 'AVAILABLE'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', '102', '88888888-8888-8888-8888-888888888888', 'AVAILABLE'),
('dddddddd-dddd-dddd-dddd-dddddddddddd', '201', '99999999-9999-9999-9999-999999999999', 'AVAILABLE'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '202', '99999999-9999-9999-9999-999999999999', 'AVAILABLE'),
('ffffffff-ffff-ffff-ffff-ffffffffffff', '301', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------
-- End of Seed Data
-- ----------------------------------------------------------
