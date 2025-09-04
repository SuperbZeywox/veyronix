package com.zeywox.veyronixcore.config.general;

import io.undertow.UndertowOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xnio.Options;

@Configuration
public class UndertowServerConfig {

    private static final Logger log = LoggerFactory.getLogger(UndertowServerConfig.class);

    @Value("${server.undertow.io-threads:0}")
    private int ioThreadsProp;

    @Value("${server.undertow.worker-threads:0}")
    private int workerThreadsProp;

    @Value("${server.tuning.backlog:8000}")
    private int backlog;

    @Bean
    UndertowServletWebServerFactory undertowFactory() {
        var f = new UndertowServletWebServerFactory();
        f.setIoThreads(16);          // = 2 * cores
        f.setWorkerThreads(64);      // VT-friendly; 64–128 is fine
        f.addBuilderCustomizers(b -> {
            b.setSocketOption(Options.BACKLOG, 8000)
                    .setSocketOption(Options.TCP_NODELAY, true)
                    .setSocketOption(Options.REUSE_ADDRESSES, true);
            b.setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                    .setServerOption(UndertowOptions.HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS, 2000)
                    .setServerOption(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE, 512 * 1024);
        });
        return f;
    }

    private int resolveCores() {
        // Prefer SPRING_CPU_CORES if provided; else use Runtime cores.
        String env = System.getenv("SPRING_CPU_CORES");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignore) {}
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    @PostConstruct
    public void logCores() {
        log.info("Detected CPU cores for tuning: {}", resolveCores());
    }
}
