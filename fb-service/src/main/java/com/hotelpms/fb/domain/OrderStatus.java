package com.hotelpms.fb.domain;

/**
 * Status of a restaurant order.
 */
public enum OrderStatus {
    /** Order has been placed. */
    PENDING,
    /** Order has been prepared. */
    PREPARED,
    /** Order has been delivered to guest. */
    DELIVERED,
    /** Order has been billed to guest's room stay. */
    BILLED_TO_ROOM
}
