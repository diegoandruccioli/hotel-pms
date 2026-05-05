package com.hotelpms.auth.domain;

/**
 * Role enum representing user authorities.
 *
 * <ul>
 *   <li>{@code ADMIN}       — system administrator; full access to all operations.</li>
 *   <li>{@code OWNER}       — hotel owner; same access as ADMIN within their hotel.</li>
 *   <li>{@code RECEPTIONIST}— front-desk operator; can manage reservations, guests,
 *       check-in/out, billing, and F&B. Cannot manage room types, delete rooms, or
 *       view financial reports.</li>
 *   <li>{@code GUEST}       — reserved for a future guest-facing portal; currently
 *       no API access is granted to this role.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    OWNER,
    RECEPTIONIST,
    GUEST
}
