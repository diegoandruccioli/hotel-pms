-- ============================================================
-- Flyway migration: V4__add_hotel_id_and_fields_to_menu_items.sql
-- Service  : fb-service (food & beverage)
-- Purpose  : Multi-tenant menu catalog (per-hotel) + CRUD fields.
--            hotel_id scopes menu items to a single hotel (IDOR-safe).
--            category, description, available support admin CRUD.
-- ============================================================

-- ---------------------------------------------------------------
-- 1. hotel_id (nullable first, then back-fill, then NOT NULL)
-- ---------------------------------------------------------------
ALTER TABLE menu_items
    ADD COLUMN IF NOT EXISTS hotel_id UUID;

-- Back-fill seed rows with the default hotel UUID used during dev/test.
UPDATE menu_items
   SET hotel_id = '00000000-0000-0000-0000-000000000001'
 WHERE hotel_id IS NULL;

ALTER TABLE menu_items
    ALTER COLUMN hotel_id SET NOT NULL;

COMMENT ON COLUMN menu_items.hotel_id
    IS 'Hotel scope for multi-tenant isolation. Set server-side; never accepted from client.';

CREATE INDEX IF NOT EXISTS idx_menu_items_hotel_id
    ON menu_items (hotel_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- 2. category, description, available
-- ---------------------------------------------------------------
ALTER TABLE menu_items
    ADD COLUMN IF NOT EXISTS category    VARCHAR(100) NOT NULL DEFAULT 'Generale',
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS available   BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN menu_items.category    IS 'Display category (e.g. Bar, Cucina, Dessert).';
COMMENT ON COLUMN menu_items.description IS 'Optional free-text description of the item.';
COMMENT ON COLUMN menu_items.available   IS 'Visibility flag for ordering; false hides the item from the order form without soft-deleting it.';
