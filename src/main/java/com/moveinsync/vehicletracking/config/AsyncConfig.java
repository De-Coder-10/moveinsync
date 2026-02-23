package com.moveinsync.vehicletracking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * EC8 — High Load During Peak Hours
 * EC9 — Event Processing Scalability
 *
 * Configures an async thread pool for non-blocking location processing.
 * - Core: 10 threads  (handles steady-state load)
 * - Max:  50 threads  (scales up during peak hours)
 * - Queue: 500 tasks  (absorbs burst traffic)
 * - CallerRunsPolicy: if queue full, calling thread processes — prevents data loss
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("locationTaskExecutor")
    public Executor locationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("location-async-");
        // Backpressure: caller thread processes when queue is full — no data loss
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
