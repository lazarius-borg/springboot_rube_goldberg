package nl.invokedynamic.demo.analytics;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import nl.invokedynamic.demo.analytics.config.JwtMultiIssuerValidator;
import nl.invokedynamic.demo.analytics.config.OpenApiConfig;
import nl.invokedynamic.demo.analytics.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsSecurityTest {

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
    void shouldRejectAnalyticsEndpointsWithoutAuthentication() {
        contextRunner.run(context -> {
            SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(filterChain);

            MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
            MockHttpServletResponse apiRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(apiReq, apiRes, new MockFilterChain());
            assertThat(apiRes.getStatus()).isEqualTo(401);
        });
    }

    @Test
    void shouldRejectCustomerFromAnalyticsEndpoints() {
        contextRunner.run(context -> {
            JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
            SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(filterChain);

            Jwt customerJwt = Jwt.withTokenValue("customer-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                    .subject("customer1")
                    .build();
            when(jwtDecoder.decode("customer-token")).thenReturn(customerJwt);

            MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
            apiReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse apiRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(apiReq, apiRes, new MockFilterChain());
            assertThat(apiRes.getStatus()).isEqualTo(403);
        });
    }

    @Test
    void shouldAllowRestaurantManagerAndAdminForAnalyticsEndpoints() {
        contextRunner.run(context -> {
            JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
            SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(filterChain);

            Jwt managerJwt = Jwt.withTokenValue("manager-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("RESTAURANT_MANAGER")))
                    .subject("manager1")
                    .build();
            when(jwtDecoder.decode("manager-token")).thenReturn(managerJwt);

            MockHttpServletRequest mgrReq = new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
            mgrReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrReq, mgrRes, new MockFilterChain());
            assertThat(mgrRes.getStatus()).isNotIn(401, 403);

            Jwt adminJwt = Jwt.withTokenValue("admin-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                    .subject("admin1")
                    .build();
            when(jwtDecoder.decode("admin-token")).thenReturn(adminJwt);

            MockHttpServletRequest adminReq = new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
            adminReq.addHeader("Authorization", "Bearer admin-token");
            MockHttpServletResponse adminRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(adminReq, adminRes, new MockFilterChain());
            assertThat(adminRes.getStatus()).isNotIn(401, 403);
        });
    }

    @Test
    void shouldAcceptExternalAndInternalIssuersInMultiIssuerValidator() {
        var validator = new JwtMultiIssuerValidator(
                List.of("http://localhost:8081/realms/rube-goldberg", "http://keycloak:8080/realms/rube-goldberg")
        );

        Jwt externalJwt = Jwt.withTokenValue("token-1")
                .header("alg", "none")
                .issuer("http://localhost:8081/realms/rube-goldberg")
                .subject("manager1")
                .build();

        Jwt internalJwt = Jwt.withTokenValue("token-2")
                .header("alg", "none")
                .issuer("http://keycloak:8080/realms/rube-goldberg")
                .subject("manager1")
                .build();

        assertThat(validator.validate(externalJwt).hasErrors()).isFalse();
        assertThat(validator.validate(internalJwt).hasErrors()).isFalse();
    }

    @Test
    void shouldRejectUntrustedIssuerInMultiIssuerValidator() {
        var validator = new JwtMultiIssuerValidator(
                List.of("http://localhost:8081/realms/rube-goldberg", "http://keycloak:8080/realms/rube-goldberg")
        );

        Jwt untrustedJwt = Jwt.withTokenValue("token-3")
                .header("alg", "none")
                .issuer("http://untrusted-auth.org/realms/rube-goldberg")
                .subject("manager1")
                .build();

        var result = validator.validate(untrustedJwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.getDescription().contains("The iss claim is not valid"));
    }

    @Test
    void shouldActivateMultiIssuerJwtDecoderUnderDockerProfile() {
        new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class)
                .withPropertyValues(
                        "spring.profiles.active=docker",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("multiIssuerJwtDecoder");
                    assertThat(context).doesNotHaveBean("singleIssuerJwtDecoder");
                });
    }

    @Test
    void shouldFallbackToSingleIssuerJwtDecoderWhenDockerProfileNotActive() {
        new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class)
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("singleIssuerJwtDecoder");
                    assertThat(context).doesNotHaveBean("multiIssuerJwtDecoder");
                });
    }
}
