package com.moveinsync.vehicletracking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// 1. Enables async & scheduling (@EnableAsync, @EnableScheduling)

// @EnableAsync activates Spring's @Async support — used by LocationAsyncService.processAsync() 
// so GPS pings return HTTP 202 immediately while processing happens in the background
// @EnableScheduling activates @Scheduled — used by AutoSimulationService.tick() which runs 
// every 1.5 seconds to advance vehicle positions
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
