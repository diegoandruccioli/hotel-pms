package com.hotelpms.guest.config;

/**
 * ThreadLocal context carrier for scheduled batch jobs that run outside an HTTP
 * request context. Allows {@link FeignHeaderConfig} to inject valid internal
 * authentication headers even when {@code RequestContextHolder} has no attributes.
 *
 * <p>Always call {@link #clear()} in a {@code finally} block to prevent leaks.
 */
public final class BatchJobContext {

    private static final ThreadLocal<BatchJobContext> CONTEXT = new ThreadLocal<>();

    private static final String BATCH_USER = "gdpr-retention-job";
    private static final String BATCH_ROLE = "ADMIN";

    private final String user;
    private final String role;
    private final String hotelId;

    private BatchJobContext(final String hotelId) {
        this.user = BATCH_USER;
        this.role = BATCH_ROLE;
        this.hotelId = hotelId;
    }

    /**
     * Sets the batch context for the current thread with the given hotel UUID.
     *
     * @param hotelId the hotel UUID string for the guest being processed
     */
    public static void set(final String hotelId) {
        CONTEXT.set(new BatchJobContext(hotelId));
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
     * Returns the system username used for batch authentication.
     *
     * @return the system username used for batch authentication
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the role used for batch authentication.
     *
     * @return the role used for batch authentication
     */
    public String getRole() {
        return role;
    }

    /**
     * Returns the hotel UUID string for the guest being processed.
     *
     * @return the hotel UUID string for the guest being processed
     */
    public String getHotelId() {
        return hotelId;
    }
}
