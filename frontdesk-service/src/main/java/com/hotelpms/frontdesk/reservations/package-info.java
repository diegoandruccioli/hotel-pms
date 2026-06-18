/**
 * Reservations domain (former reservation-service): booking creation, overlap
 * detection, status lifecycle. Calls the {@code rooms} domain in-process for
 * room data (formerly an inventory-service Feign client, see ADR-001).
 */
package com.hotelpms.frontdesk.reservations;
