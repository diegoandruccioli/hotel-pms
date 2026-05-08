-- ============================================================
-- Flyway migration: V2__add_menu_items.sql
-- Service       : fb-service (food & beverage)
-- Purpose       : Server-side price catalog for F&B orders.
--                 Clients reference items by UUID; the service
--                 looks up the canonical price server-side,
--                 preventing client-side price tampering (T-FB-02).
-- ============================================================

CREATE TABLE IF NOT EXISTS menu_items (
    id         UUID           NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255)   NOT NULL,
    price      NUMERIC(10, 2) NOT NULL,
    active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,

    CONSTRAINT pk_menu_items        PRIMARY KEY (id),
    CONSTRAINT chk_menu_items_price CHECK (price >= 0)
);

COMMENT ON TABLE  menu_items            IS 'F&B price catalog. Prices are authoritative server-side; clients never supply prices.';
COMMENT ON COLUMN menu_items.id         IS 'Surrogate UUID primary key.';
COMMENT ON COLUMN menu_items.name       IS 'Display name of the menu item.';
COMMENT ON COLUMN menu_items.price      IS 'Canonical unit price in EUR. Set by hotel management only.';
COMMENT ON COLUMN menu_items.active     IS 'Soft-delete flag; inactive items are hidden from ordering.';
COMMENT ON COLUMN menu_items.created_at IS 'Record creation timestamp.';
COMMENT ON COLUMN menu_items.updated_at IS 'Last modification timestamp.';

CREATE INDEX IF NOT EXISTS idx_menu_items_active
    ON menu_items (active)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- Seed data: typical hotel F&B items
-- ---------------------------------------------------------------
INSERT INTO menu_items (id, name, price) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'Espresso',              2.50),
    ('a1000000-0000-0000-0000-000000000002', 'Cappuccino',            3.00),
    ('a1000000-0000-0000-0000-000000000003', 'Acqua Minerale 500ml',  2.00),
    ('a1000000-0000-0000-0000-000000000004', 'Succo di Frutta',       3.50),
    ('a1000000-0000-0000-0000-000000000005', 'Croissant',             2.50),
    ('a1000000-0000-0000-0000-000000000006', 'Club Sandwich',        12.00),
    ('a1000000-0000-0000-0000-000000000007', 'Caesar Salad',         10.00),
    ('a1000000-0000-0000-0000-000000000008', 'Pasta del Giorno',     14.00),
    ('a1000000-0000-0000-0000-000000000009', 'Bistecca ai Ferri',    22.00),
    ('a1000000-0000-0000-0000-000000000010', 'Tiramisù',              6.00);
