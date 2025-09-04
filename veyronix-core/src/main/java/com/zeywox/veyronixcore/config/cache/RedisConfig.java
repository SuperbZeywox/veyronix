package com.zeywox.veyronixcore.config.cache;


import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(RedisProperties props) {
        Duration timeout = (props.getTimeout() != null) ? props.getTimeout() : Duration.ofSeconds(2);
        LettuceClientConfiguration clientCfg = buildLettuceClientConfiguration(props, timeout);

        if (props.getCluster() != null && hasNodes(props.getCluster().getNodes())) {
            RedisClusterConfiguration cfg = new RedisClusterConfiguration(props.getCluster().getNodes());
            if (props.getPassword() != null) cfg.setPassword(RedisPassword.of(props.getPassword()));
            return new LettuceConnectionFactory(cfg, clientCfg);
        }

        if (props.getSentinel() != null
                && props.getSentinel().getMaster() != null
                && hasNodes(props.getSentinel().getNodes())) {
            RedisSentinelConfiguration cfg = new RedisSentinelConfiguration();
            cfg.master(props.getSentinel().getMaster());
            for (String node : props.getSentinel().getNodes()) {
                String[] hp = node.split(":", 2);
                cfg.sentinel(hp[0], Integer.parseInt(hp[1]));
            }
            if (props.getPassword() != null) cfg.setPassword(RedisPassword.of(props.getPassword()));
            return new LettuceConnectionFactory(cfg, clientCfg);
        }

        RedisStandaloneConfiguration cfg =
                new RedisStandaloneConfiguration(props.getHost(), props.getPort());
        cfg.setDatabase(props.getDatabase());              // Boot 3.x getter is primitive int
        if (props.getPassword() != null) cfg.setPassword(RedisPassword.of(props.getPassword()));
        return new LettuceConnectionFactory(cfg, clientCfg);
    }

    private static LettuceClientConfiguration buildLettuceClientConfiguration(RedisProperties props, Duration timeout) {
        RedisProperties.Lettuce lettuce = props.getLettuce();
        RedisProperties.Pool pool = (lettuce != null ? lettuce.getPool() : null);

        if (pool != null) {
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolCfg =
                    new GenericObjectPoolConfig<>();

            poolCfg.setMaxTotal(pool.getMaxActive());
            poolCfg.setMaxIdle(pool.getMaxIdle());
            poolCfg.setMinIdle(pool.getMinIdle());
            if (pool.getMaxWait() != null) {
                poolCfg.setMaxWait(pool.getMaxWait());
            }

            return LettucePoolingClientConfiguration.builder()
                    .commandTimeout(timeout)
                    .poolConfig(poolCfg)
                    .build();
        }

        return LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();
    }

    private static boolean hasNodes(List<String> nodes) {
        return nodes != null && !nodes.isEmpty();
    }

    @Bean("storeStringRedisTemplate")
    public StringRedisTemplate storeStringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

}



