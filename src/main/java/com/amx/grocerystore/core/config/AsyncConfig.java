package com.amx.grocerystore.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(200);
        executor.setTaskTerminationTimeout(30_000);
        return executor;
    }

    /**
     * Dedicated executor for outbound owner/customer notifications. Isolated from the default
     * {@code taskExecutor} so a burst of low-stock/sale/inventory alerts (each blocking up to ~15s on a
     * slow WhatsApp/Telegram gateway) cannot starve other {@code @Async} work (e.g. audit writes) nor pin
     * the committing request thread behind the shared throttle. Own concurrency knob; widen only when the
     * gateway is healthy — never to mask a slow upstream.
     */
    @Bean(name = "notificationExecutor")
    public AsyncTaskExecutor notificationExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("notif-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(200);
        executor.setTaskTerminationTimeout(30_000);
        return executor;
    }
}
