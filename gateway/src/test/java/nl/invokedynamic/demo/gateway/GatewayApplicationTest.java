package nl.invokedynamic.demo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class GatewayApplicationTest {

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void contextLoads() {
    }
}
