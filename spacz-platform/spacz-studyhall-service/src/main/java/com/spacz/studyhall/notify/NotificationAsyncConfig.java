package com.spacz.studyhall.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    /** When the queue is full the notification is dropped (and logged), never failing the caller. */
    @Bean(name = "notificationForwardExecutor")
    public Executor notificationForwardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("notify-fwd-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Notification queue full; a notification was dropped"));
        executor.initialize();
        return executor;
    }
}
