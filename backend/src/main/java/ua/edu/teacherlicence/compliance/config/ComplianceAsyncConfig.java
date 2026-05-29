package ua.edu.teacherlicence.compliance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Окремий TaskExecutor для async compliance refresh.
 * Ізольований від Spring default і WebSocket executors — щоб avalanche-refresh
 * (напр. після import 400 teachers) не блокував інші @Async задачі.
 */
@Configuration
public class ComplianceAsyncConfig {

    public static final String COMPLIANCE_EXECUTOR = "complianceExecutor";

    @Bean(COMPLIANCE_EXECUTOR)
    public Executor complianceExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("compliance-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
