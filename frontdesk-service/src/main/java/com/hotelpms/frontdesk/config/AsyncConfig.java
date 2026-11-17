package com.hotelpms.frontdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables {@code @Async} and defines the bounded pool used for the check-out email,
 * so that a slow PDF render or mail relay never holds a request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    /**
     * Pool for {@link com.hotelpms.frontdesk.stays.service.impl.StayCheckoutEmailListener}.
     * When saturated, {@link ThreadPoolExecutor.CallerRunsPolicy} runs the email on the
     * calling thread: the old (slow) behavior rather than a silently lost email.
     * Pending emails are given time to finish on shutdown.
     *
     * @return the executor, named {@code checkoutEmailExecutor}
     */
    @Bean(name = "checkoutEmailExecutor")
    public ThreadPoolTaskExecutor checkoutEmailExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("checkout-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
