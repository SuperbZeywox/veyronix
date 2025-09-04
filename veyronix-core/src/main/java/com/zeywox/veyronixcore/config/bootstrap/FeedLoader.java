package com.zeywox.veyronixcore.config.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeywox.veyronixcore.dto.ProductIn;
import com.zeywox.veyronixcore.services.FeedIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class FeedLoader {
    private static final Logger log = LoggerFactory.getLogger(FeedLoader.class);

    @Value("${feed.url}")
    private String feedUrl;

    @Bean
    WebClient webClient() {
        return WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB safeguard
                .build();
    }

    @Bean
    ApplicationRunner loadOnStartup(WebClient client, ObjectMapper om, FeedIngestionService feedIngestionService) {
        return args -> {
            try {
                if (feedUrl == null || feedUrl.isBlank()) {
                    log.warn("feed.url is not configured; skipping ingest.");
                    return;
                }

                log.info("Fetching feed from {}", feedUrl);
                String json = client.get()
                        .uri(feedUrl)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorResume(e -> {
                            log.error("Failed to fetch feed: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .block();

                if (json == null || json.isBlank()) {
                    log.warn("Feed empty or not reachable; skipping ingest.");
                    return;
                }

                List<ProductIn> items = om.readValue(json, new TypeReference<List<ProductIn>>() {});
                if (items.isEmpty()) {
                    log.warn("Feed parsed but contained 0 items; skipping ingest.");
                    return;
                }

                var ingested = feedIngestionService.ingest(items);
                log.info("Ingested {} products into store", ingested.size());
            } catch (Exception e) {
                log.error("Feed ingest error", e);
            }
        };
    }
}

