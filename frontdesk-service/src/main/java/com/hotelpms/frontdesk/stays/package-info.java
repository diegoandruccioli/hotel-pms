/**
 * Stays domain (former stay-service): check-in/check-out lifecycle, hotel
 * operational settings, and Alloggiati Web (Polizia di Stato) police
 * reporting. Calls the {@code rooms} and {@code reservations} domains
 * in-process (formerly Feign clients to inventory-service / reservation-service,
 * see ADR-001).
 */
package com.hotelpms.frontdesk.stays;
