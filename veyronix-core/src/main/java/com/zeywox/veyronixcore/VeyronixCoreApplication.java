package com.zeywox.veyronixcore;

import com.zeywox.veyronixcore.config.cache.ResponseCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ResponseCacheProperties.class)
public class VeyronixCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeyronixCoreApplication.class, args);
    }

}
