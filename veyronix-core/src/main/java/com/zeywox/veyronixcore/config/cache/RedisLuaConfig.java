package com.zeywox.veyronixcore.config.cache;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class RedisLuaConfig {

    @Bean
    public DefaultRedisScript<Long> productUpsertScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setResultType(Long.class);
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/product_upsert.lua")));
        return s;
    }

    @Bean
    public DefaultRedisScript<Long> productSetStockScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setResultType(Long.class);
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/product_set_stock.lua")));
        return s;
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> zidxSeedAndRangeScript() {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setResultType(List.class);
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/zidx_seed_and_range.lua")));
        return s;
    }
}
