package nl.invokedynamic.demo.restaurant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import nl.invokedynamic.demo.restaurant.config.OpenApiConfig;
import nl.invokedynamic.demo.restaurant.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RestaurantSecurityTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withBean(JwtDecoder.class, () -> mock(JwtDecoder.class));

    @Test
    void shouldConfigureOpenApiWithBearerAuth() {
        SecurityScheme scheme = OpenApiConfig.class.getAnnotation(SecurityScheme.class);
        assertThat(scheme).isNotNull();
        assertThat(scheme.name()).isEqualTo("bearerAuth");
        assertThat(scheme.type()).isEqualTo(SecuritySchemeType.HTTP);
        assertThat(scheme.scheme()).isEqualTo("bearer");
        assertThat(scheme.bearerFormat()).isEqualTo("JWT");

        OpenAPIDefinition def = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);
        assertThat(def).isNotNull();
        assertThat(def.security()).hasSize(1);
        assertThat(def.security()[0].name()).isEqualTo("bearerAuth");
    }

    @Test
    void shouldPermitSwaggerAndDocsEndpointsWithoutAuthentication() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(filterChain);

            // Swagger UI HTML
            MockHttpServletRequest swaggerHtmlReq = new MockHttpServletRequest("GET", "/swagger-ui.html");
            MockHttpServletResponse swaggerHtmlRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(swaggerHtmlReq, swaggerHtmlRes, new MockFilterChain());
            assertThat(swaggerHtmlRes.getStatus()).isNotEqualTo(401);

            // Swagger UI assets
            MockHttpServletRequest swaggerUiReq = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
            MockHttpServletResponse swaggerUiRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(swaggerUiReq, swaggerUiRes, new MockFilterChain());
            assertThat(swaggerUiRes.getStatus()).isNotEqualTo(401);

            // OpenAPI JSON spec
            MockHttpServletRequest apiDocsReq = new MockHttpServletRequest("GET", "/v3/api-docs");
            MockHttpServletResponse apiDocsRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(apiDocsReq, apiDocsRes, new MockFilterChain());
            assertThat(apiDocsRes.getStatus()).isNotEqualTo(401);
        });
    }

    @Test
    void shouldRejectBusinessAndActuatorEndpointsWithoutAuthentication() {
        contextRunner.run(context -> {
            SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(filterChain);

            // Business endpoint without token
            MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/restaurants");
            MockHttpServletResponse apiRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(apiReq, apiRes, new MockFilterChain());
            assertThat(apiRes.getStatus()).isEqualTo(401);

            // Actuator endpoint without token
            MockHttpServletRequest actuatorReq = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse actuatorRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(actuatorReq, actuatorRes, new MockFilterChain());
            assertThat(actuatorRes.getStatus()).isEqualTo(401);
        });
    }
}
