package com.grupocordillera.api_gateway;

import com.grupocordillera.api_gateway.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
