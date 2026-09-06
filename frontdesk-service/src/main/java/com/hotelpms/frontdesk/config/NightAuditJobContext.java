package com.hotelpms.frontdesk.config;

/**
 * ThreadLocal context carrier for the scheduled night-audit job, which runs
 * outside an HTTP request context. Allows {@link FeignHeaderConfig} to inject
 * valid internal authentication headers on its Feign call to billing-service
 * (the cash-closing summary) when {@code RequestContextHolder} has no
 * attributes. Mirrors guest-service's {@code BatchJobContext} — see that
 * class for the fuller rationale.
 *
 * <p>Always call {@link #clear()} in a {@code finally} block to prevent leaks.
 */
public final class NightAuditJobContext {

    private static final ThreadLocal<NightAuditJobContext> CONTEXT = new ThreadLocal<>();

    private static final String JOB_USER = "night-audit-job";
    private static final String JOB_ROLE = "ADMIN";

    private final String hotelId;

    private NightAuditJobContext(final String hotelId) {
        this.hotelId = hotelId;
    }

    /**
     * Sets the batch context for the current thread with the given hotel UUID.
     *
     * @param hotelId the hotel UUID string being audited
     */
    public static void set(final String hotelId) {
        CONTEXT.set(new NightAuditJobContext(hotelId));
    }

    /**
     * Returns the batch context for the current thread, or {@code null} if not set.
     *
     * @return the current {@link NightAuditJobContext}, or {@code null}
     */
    public static NightAuditJobContext get() {
        return CONTEXT.get();
    }

    /**
     * Clears the batch context for the current thread. Must be called in a
     * {@code finally} block to prevent ThreadLocal leaks.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Returns the system username used for the night-audit job's outgoing calls.
     *
     * @return the system username
     */
    public String getUser() {
        return JOB_USER;
    }

    /**
     * Returns the role used for the night-audit job's outgoing calls.
     *
     * @return the role
     */
    public String getRole() {
        return JOB_ROLE;
    }

    /**
     * Returns the hotel UUID string being audited.
     *
     * @return the hotel UUID string
     */
    public String getHotelId() {
        return hotelId;
    }
}
