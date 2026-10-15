package com.capstone.etp.executor;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class TradeExecutorApplicationTest {

    @Test
    void main_delegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            TradeExecutorApplication.main(new String[]{"--spring.main.web-application-type=none"});
            mocked.verify(() -> SpringApplication.run(TradeExecutorApplication.class,
                    new String[]{"--spring.main.web-application-type=none"}));
        }
    }
}
