package com.zeywox.veyronixcore.config.cache;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CacheFillExecutorConfig {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService cacheFillExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}




