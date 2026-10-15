package com.capstone.etp.executor.config;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MyBatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration
                .getTypeHandlerRegistry()
                .register(UUID.class, UUIDTypeHandler.class);
    }
}
