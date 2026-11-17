package com.hotelpms.frontdesk.stays.service.impl;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Stands in for Spring's after-commit, asynchronous event delivery in plain unit tests:
 * hands every {@link StayCheckedOutEvent} straight to the listener on the calling thread,
 * so tests of {@code StayServiceImpl.checkOut} can still assert on the resulting email.
 * Events published before {@link #deliverTo} is called (or other event types) are ignored.
 */
final class CheckoutEmailTestPublisher implements ApplicationEventPublisher {

    private StayCheckoutEmailListener listener;

    /**
     * Wires the listener that receives the events.
     *
     * @param target the listener under test
     */
    void deliverTo(final StayCheckoutEmailListener target) {
        this.listener = target;
    }

    @Override
    public void publishEvent(final Object event) {
        if (listener != null && event instanceof StayCheckedOutEvent checkedOut) {
            listener.onStayCheckedOut(checkedOut);
        }
    }
}
