package com.hotelpms.auth.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a deliberate exception to the hotel_id-scoping ArchUnit rule
 * ({@code TenantIsolationArchTest}) for a repository method on a tenant-root
 * entity that does not filter by hotelId directly.
 *
 * <p>Every use must justify <em>why</em> the method is still tenant-safe
 * (e.g. its parameters are already resolved through a hotel-scoped lookup
 * upstream, or it's an intentionally platform-wide operation such as a
 * scheduled batch job). This is a conscious, reviewed decision — not a way
 * to silence the rule.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TenantScopeExempt {

    /**
     * Why this method is safe without a direct hotelId filter.
     *
     * @return the justification
     */
    String reason();
}
