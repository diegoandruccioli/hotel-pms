-- Round 1 exploratory test bug #3: restaurant_orders.room_number was VARCHAR(20),
-- but frontdesk-service's rooms.room_number/stays.room_number (the source this
-- column is denormalized from at order-creation time) are VARCHAR(50) — a room
-- name longer than 20 characters (valid at creation in frontdesk-service) crashed
-- POST /api/v1/fb/orders with a raw 500 (DataException: value too long for type
-- character varying(20)). Widened to match the real upstream constraint.
ALTER TABLE restaurant_orders
    ALTER COLUMN room_number TYPE VARCHAR(50);
