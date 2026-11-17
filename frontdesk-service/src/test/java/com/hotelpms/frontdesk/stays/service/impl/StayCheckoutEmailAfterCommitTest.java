package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.config.AsyncConfig;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires the real {@code @Async} + {@code @TransactionalEventListener} machinery (with a
 * no-op transaction manager, no database) to prove the two properties the fix relies on:
 * nothing is sent when the check-out rolls back, and after a commit the email runs on the
 * {@code checkout-email-} pool, not on the thread that did the check-out.
 */
@SpringJUnitConfig(StayCheckoutEmailAfterCommitTest.TestConfig.class)
class StayCheckoutEmailAfterCommitTest {

    private static final int WAIT_SECONDS = 5;
    private static final int NEGATIVE_WAIT_MILLIS = 500;
    private static final String POOL_PREFIX = "checkout-email-";
    private static final String PAID_STATUS = "PAID";

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private StayRepository stayRepository;
    @Autowired
    private StayNotificationCoordinator coordinator;

    @Test
    void shouldSendTheEmailOnThePoolThreadOnlyAfterCommit()
            throws InterruptedException, ExecutionException, TimeoutException {
        final UUID stayId = UUID.randomUUID();
        final UUID hotelId = UUID.randomUUID();
        final Stay stay = Stay.builder().id(stayId).hotelId(hotelId).build();
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        final CompletableFuture<String> emailThread = new CompletableFuture<>();
        doAnswer(call -> emailThread.complete(Thread.currentThread().getName()))
                .when(coordinator).sendCheckoutEmailIfPossible(any(), any());
        final InvoiceStatusResponse invoice =
                new InvoiceStatusResponse(UUID.randomUUID(), UUID.randomUUID(), PAID_STATUS, BigDecimal.TEN);

        final String callerThread = Thread.currentThread().getName();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(new StayCheckedOutEvent(stayId, hotelId, invoice));
            assertFalse(emailThread.isDone(), "the email must not run before the commit");
        });

        final String pooled = emailThread.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertTrue(pooled.startsWith(POOL_PREFIX), pooled);
        assertNotEquals(callerThread, pooled);
    }

    @Test
    void shouldNotSendAnyEmailWhenTheCheckoutRollsBack() {
        final UUID stayId = UUID.randomUUID();
        final UUID hotelId = UUID.randomUUID();
        final InvoiceStatusResponse invoice =
                new InvoiceStatusResponse(UUID.randomUUID(), UUID.randomUUID(), PAID_STATUS, BigDecimal.TEN);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(new StayCheckedOutEvent(stayId, hotelId, invoice));
            status.setRollbackOnly();
        });

        verify(stayRepository, after(NEGATIVE_WAIT_MILLIS).never()).findByIdAndHotelId(stayId, hotelId);
        verify(coordinator, never()).sendCheckoutEmailIfPossible(any(), any());
    }

    @Configuration
    @EnableTransactionManagement
    @Import({AsyncConfig.class, StayCheckoutEmailListener.class})
    static class TestConfig {

        @Bean
        StayRepository stayRepository() {
            return mock(StayRepository.class);
        }

        @Bean
        StayNotificationCoordinator stayNotificationCoordinator() {
            return mock(StayNotificationCoordinator.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }
    }

    /** Runs the commit/rollback lifecycle (and so the transaction synchronizations) with no resource. */
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(final Object transaction, final TransactionDefinition definition) {
            // no resource to bind
        }

        @Override
        protected void doCommit(final DefaultTransactionStatus status) {
            // nothing to commit
        }

        @Override
        protected void doRollback(final DefaultTransactionStatus status) {
            // nothing to roll back
        }
    }
}
