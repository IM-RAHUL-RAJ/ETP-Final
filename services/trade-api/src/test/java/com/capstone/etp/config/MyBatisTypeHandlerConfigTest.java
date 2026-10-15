package com.capstone.etp.config;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisTypeHandlerConfigTest {

    @Test
    void uuidTypeHandlerCustomizer_registersUuidHandler() {
        MyBatisTypeHandlerConfig config = new MyBatisTypeHandlerConfig();
        ConfigurationCustomizer customizer = config.uuidTypeHandlerCustomizer();

        Configuration myBatisConfig = mock(Configuration.class);
        TypeHandlerRegistry registry = mock(TypeHandlerRegistry.class);
        when(myBatisConfig.getTypeHandlerRegistry()).thenReturn(registry);

        assertNotNull(customizer);
        customizer.customize(myBatisConfig);

        verify(registry).register(UUID.class, UUIDTypeHandler.class);
    }
}
