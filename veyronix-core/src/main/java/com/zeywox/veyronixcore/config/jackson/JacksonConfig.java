package com.zeywox.veyronixcore.config.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.zeywox.veyronixcore.deserialization.PatchProductNormalizationModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module patchProductNormalizationModule() {
        return new PatchProductNormalizationModule();
    }

    @Bean
    public AfterburnerModule afterburnerModule() {
        return new AfterburnerModule();
    }

    @Bean
    public ObjectMapper objectMapper(AfterburnerModule afterburnerModule, com.fasterxml.jackson.databind.Module patchProductNormalizationModule) {
        return new ObjectMapper()
                .registerModule(afterburnerModule)
                .registerModule(patchProductNormalizationModule)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }


}
