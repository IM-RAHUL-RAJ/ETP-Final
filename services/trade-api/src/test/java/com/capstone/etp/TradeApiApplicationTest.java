package com.capstone.etp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class TradeApiApplicationTest {

    @Test
    void main_delegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            TradeApiApplication.main(new String[]{"--spring.main.web-application-type=none"});
            mocked.verify(() -> SpringApplication.run(TradeApiApplication.class,
                    new String[]{"--spring.main.web-application-type=none"}));
        }
    }
}