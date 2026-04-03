-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : fb-service (food & beverage)
-- Schema owner  : JPA entities RestaurantOrder and OrderItem
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- Enums         : OrderStatus → OPEN, IN_PROGRESS, SERVED, CANCELLED
-- Cross-service : stay_id is a logical ref to stay-service (no DB FK)
-- ============================================================

-- ---------------------------------------------------------------
-- restaurant_orders
-- Maps to: @Entity @Table(name = "restaurant_orders") RestaurantOrder.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS restaurant_orders (
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    stay_id      UUID           NOT NULL,
    order_date   TIMESTAMP      NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL,
    -- updated_at has insertable=false in the entity, so it may be NULL on first insert
    updated_at   TIMESTAMP,

    CONSTRAINT pk_restaurant_orders           PRIMARY KEY (id),
    CONSTRAINT chk_restaurant_orders_status   CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'SERVED', 'CANCELLED'
    )),
    CONSTRAINT chk_restaurant_orders_total    CHECK (total_amount >= 0)
);

COMMENT ON TABLE  restaurant_orders              IS 'Food and beverage orders placed by in-house guests from their stay.';
COMMENT ON COLUMN restaurant_orders.id           IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN restaurant_orders.stay_id      IS 'Logical reference to an active stay in stay-service (cross-service, no DB FK).';
COMMENT ON COLUMN restaurant_orders.order_date   IS 'Timestamp when the order was placed.';
COMMENT ON COLUMN restaurant_orders.total_amount IS 'Sum of (unit_price × quantity) for all items in this order.';
COMMENT ON COLUMN restaurant_orders.status       IS 'Order lifecycle: OPEN | IN_PROGRESS | SERVED | CANCELLED.';
COMMENT ON COLUMN restaurant_orders.active       IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN restaurant_orders.created_at   IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN restaurant_orders.updated_at   IS 'Last modification timestamp, managed by Spring Data Auditing (insertable=false).';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_active
    ON restaurant_orders (active)
    WHERE active = TRUE;

-- Index for status-based kitchen / service dashboard queries
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_status
    ON restaurant_orders (status)
    WHERE active = TRUE;

-- Index for cross-service lookup: all orders for a stay
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_stay_id
    ON restaurant_orders (stay_id)
    WHERE active = TRUE;

-- Temporal index for shift-based reporting
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_order_date
    ON restaurant_orders (order_date)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- order_items
-- Maps to: @Entity @Table(name = "order_items") OrderItem.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    restaurant_order_id UUID           NOT NULL,
    item_name           VARCHAR(255)   NOT NULL,
    quantity            INTEGER        NOT NULL,
    unit_price          NUMERIC(10, 2) NOT NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL,
    -- updated_at has insertable=false in the entity, so it may be NULL on first insert
    updated_at          TIMESTAMP,

    CONSTRAINT pk_order_items                   PRIMARY KEY (id),
    CONSTRAINT fk_order_items_restaurant_order  FOREIGN KEY (restaurant_order_id)
                                                    REFERENCES restaurant_orders (id)
                                                    ON UPDATE CASCADE
                                                    ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity         CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price       CHECK (unit_price >= 0)
);

COMMENT ON TABLE  order_items                       IS 'Line items within a restaurant order (food/beverage course).';
COMMENT ON COLUMN order_items.id                    IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN order_items.restaurant_order_id   IS 'FK → restaurant_orders.id; the parent order.';
COMMENT ON COLUMN order_items.item_name             IS 'Name of the menu item ordered.';
COMMENT ON COLUMN order_items.quantity              IS 'Number of portions. Must be at least 1.';
COMMENT ON COLUMN order_items.unit_price            IS 'Price per single portion at the time of ordering.';
COMMENT ON COLUMN order_items.active                IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN order_items.created_at            IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN order_items.updated_at            IS 'Last modification timestamp, managed by Spring Data Auditing (insertable=false).';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_order_items_active
    ON order_items (active)
    WHERE active = TRUE;

-- Index on FK to accelerate restaurant_order → items JOIN
CREATE INDEX IF NOT EXISTS idx_order_items_restaurant_order_id
    ON order_items (restaurant_order_id);
