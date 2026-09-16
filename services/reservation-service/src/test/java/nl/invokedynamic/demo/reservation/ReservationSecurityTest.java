package nl.invokedynamic.demo.reservation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import nl.invokedynamic.demo.reservation.config.JwtMultiIssuerValidator;
import nl.invokedynamic.demo.reservation.config.OpenApiConfig;
import nl.invokedynamic.demo.reservation.config.SecurityConfig;
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

class ReservationSecurityTest {

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
            MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/reservations");
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

    @Test
    void shouldAcceptExternalAndInternalIssuersInMultiIssuerValidator() {
        var validator = new JwtMultiIssuerValidator(
                List.of("http://localhost:8081/realms/rube-goldberg", "http://keycloak:8080/realms/rube-goldberg")
        );

        Jwt externalJwt = Jwt.withTokenValue("token-1")
                .header("alg", "none")
                .issuer("http://localhost:8081/realms/rube-goldberg")
                .subject("test-user")
                .build();

        Jwt internalJwt = Jwt.withTokenValue("token-2")
                .header("alg", "none")
                .issuer("http://keycloak:8080/realms/rube-goldberg")
                .subject("test-user")
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
                .subject("test-user")
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

    @Test
    void shouldEnforceRbacOnReservationEndpoints() {
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

            Jwt managerJwt = Jwt.withTokenValue("manager-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("RESTAURANT_MANAGER")))
                    .subject("manager1")
                    .build();
            when(jwtDecoder.decode("manager-token")).thenReturn(managerJwt);

            // 1. Customer can book reservation (POST /api/v1/reservations)
            MockHttpServletRequest bookReq = new MockHttpServletRequest("POST", "/api/v1/reservations");
            bookReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse bookRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(bookReq, bookRes, new MockFilterChain());
            assertThat(bookRes.getStatus()).isNotIn(401, 403);

            // 2. Manager CAN book reservation (POST /api/v1/reservations) for back-filling
            MockHttpServletRequest mgrBookReq = new MockHttpServletRequest("POST", "/api/v1/reservations");
            mgrBookReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrBookRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrBookReq, mgrBookRes, new MockFilterChain());
            assertThat(mgrBookRes.getStatus()).isNotIn(401, 403);

            // 3. Customer CANNOT query restaurant-wide reservations (GET /api/v1/reservations) -> 403
            MockHttpServletRequest listReq = new MockHttpServletRequest("GET", "/api/v1/reservations");
            listReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse listRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(listReq, listRes, new MockFilterChain());
            assertThat(listRes.getStatus()).isEqualTo(403);

            // 4. Manager CAN query restaurant-wide reservations (GET /api/v1/reservations)
            MockHttpServletRequest mgrListReq = new MockHttpServletRequest("GET", "/api/v1/reservations");
            mgrListReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrListRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrListReq, mgrListRes, new MockFilterChain());
            assertThat(mgrListRes.getStatus()).isNotIn(401, 403);

            // 5. Customer CANNOT update reservation status (PATCH /api/v1/reservations/123/status) -> 403
            MockHttpServletRequest statusReq = new MockHttpServletRequest("PATCH", "/api/v1/reservations/123/status");
            statusReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse statusRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(statusReq, statusRes, new MockFilterChain());
            assertThat(statusRes.getStatus()).isEqualTo(403);

            // 6. Manager CAN update reservation status (PATCH /api/v1/reservations/123/status)
            MockHttpServletRequest mgrStatusReq = new MockHttpServletRequest("PATCH", "/api/v1/reservations/123/status");
            mgrStatusReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrStatusRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrStatusReq, mgrStatusRes, new MockFilterChain());
            assertThat(mgrStatusRes.getStatus()).isNotIn(401, 403);

            // 7. Both Customer and Manager CAN inspect individual reservation (GET /api/v1/reservations/123)
            MockHttpServletRequest getCustReq = new MockHttpServletRequest("GET", "/api/v1/reservations/123");
            getCustReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse getCustRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(getCustReq, getCustRes, new MockFilterChain());
            assertThat(getCustRes.getStatus()).isNotIn(401, 403);

            MockHttpServletRequest getMgrReq = new MockHttpServletRequest("GET", "/api/v1/reservations/123");
            getMgrReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse getMgrRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(getMgrReq, getMgrRes, new MockFilterChain());
            assertThat(getMgrRes.getStatus()).isNotIn(401, 403);

            // 8. Both Customer and Manager CAN cancel individual reservation (DELETE /api/v1/reservations/123)
            MockHttpServletRequest delCustReq = new MockHttpServletRequest("DELETE", "/api/v1/reservations/123");
            delCustReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse delCustRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(delCustReq, delCustRes, new MockFilterChain());
            assertThat(delCustRes.getStatus()).isNotIn(401, 403);

            MockHttpServletRequest delMgrReq = new MockHttpServletRequest("DELETE", "/api/v1/reservations/123");
            delMgrReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse delMgrRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(delMgrReq, delMgrRes, new MockFilterChain());
            assertThat(delMgrRes.getStatus()).isNotIn(401, 403);
        });
    }
}
