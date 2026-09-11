package com.hotelpms.frontdesk.config;

/**
 * ThreadLocal context carrier for scheduled jobs that run outside an HTTP
 * request context. Allows {@link FeignHeaderConfig} to inject valid internal
 * authentication headers on outbound Feign calls (e.g. to billing-service)
 * when {@code RequestContextHolder} has no attributes bound to the current
 * thread. Mirrors guest-service's {@code BatchJobContext} — see that class
 * for the fuller rationale.
 *
 * <p>Shared by every frontdesk-service scheduled job (night audit, GDPR
 * retention, ...) — each job sets its own {@code jobUser} so the audit trail
 * (T-AUTH-05) can tell which job originated a given internal call.
 *
 * <p>Always call {@link #clear()} in a {@code finally} block to prevent leaks.
 */
public final class BatchJobContext {

    private static final ThreadLocal<BatchJobContext> CONTEXT = new ThreadLocal<>();

    private static final String JOB_ROLE = "ADMIN";

    private final String jobUser;
    private final String hotelId;

    private BatchJobContext(final String jobUser, final String hotelId) {
        this.jobUser = jobUser;
        this.hotelId = hotelId;
    }

    /**
     * Sets the batch context for the current thread.
     *
     * @param jobUser the system username identifying the calling job (e.g.
     *                {@code "night-audit-job"}, {@code "gdpr-retention-job"})
     * @param hotelId the hotel UUID string the job is currently processing
     */
    public static void set(final String jobUser, final String hotelId) {
        CONTEXT.set(new BatchJobContext(jobUser, hotelId));
    }

    /**
     * Returns the batch context for the current thread, or {@code null} if not set.
     *
     * @return the current {@link BatchJobContext}, or {@code null}
     */
    public static BatchJobContext get() {
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
     * Returns the system username used for this job's outgoing calls.
     *
     * @return the system username
     */
    public String getUser() {
        return jobUser;
    }

    /**
     * Returns the role used for this job's outgoing calls.
     *
     * @return the role
     */
    public String getRole() {
        return JOB_ROLE;
    }

    /**
     * Returns the hotel UUID string being processed.
     *
     * @return the hotel UUID string
     */
    public String getHotelId() {
        return hotelId;
    }
}
