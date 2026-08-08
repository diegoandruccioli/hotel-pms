/**
 * Pricing domain: resolves the nightly rate for a room type on a given date,
 * falling back to {@code RoomType.basePrice} when no {@code RateSeason} covers
 * it. Consumed in-process by the reservations and stays domains within this
 * service (the reservation snapshot at booking time, the walk-in charge at
 * check-in) — see {@code RatePricingService}.
 */
package com.hotelpms.frontdesk.pricing;
