package nl.invokedynamic.demo.waitinglist;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import nl.invokedynamic.demo.waitinglist.config.JwtMultiIssuerValidator;
import nl.invokedynamic.demo.waitinglist.config.OpenApiConfig;
import nl.invokedynamic.demo.waitinglist.config.SecurityConfig;
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

class WaitingListSecurityTest {

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
            MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/waiting-list");
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
    void shouldEnforceCustomerRoleOnWaitingListEndpoints() {
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

            // 1. Customer can join waiting list
            MockHttpServletRequest joinReq = new MockHttpServletRequest("POST", "/api/v1/waiting-list");
            joinReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse joinRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(joinReq, joinRes, new MockFilterChain());
            assertThat(joinRes.getStatus()).isNotIn(401, 403);

            // 2. Manager without customer role CANNOT join waiting list -> 403
            MockHttpServletRequest mgrJoinReq = new MockHttpServletRequest("POST", "/api/v1/waiting-list");
            mgrJoinReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrJoinRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrJoinReq, mgrJoinRes, new MockFilterChain());
            assertThat(mgrJoinRes.getStatus()).isEqualTo(403);

            // 3. Customer can accept offer
            MockHttpServletRequest acceptReq = new MockHttpServletRequest("POST", "/api/v1/waiting-list/offers/123/accept");
            acceptReq.addHeader("Authorization", "Bearer customer-token");
            MockHttpServletResponse acceptRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(acceptReq, acceptRes, new MockFilterChain());
            assertThat(acceptRes.getStatus()).isNotIn(401, 403);

            // 4. Manager without customer role CANNOT accept offer -> 403
            MockHttpServletRequest mgrAcceptReq = new MockHttpServletRequest("POST", "/api/v1/waiting-list/offers/123/accept");
            mgrAcceptReq.addHeader("Authorization", "Bearer manager-token");
            MockHttpServletResponse mgrAcceptRes = new MockHttpServletResponse();
            filterChainProxy.doFilter(mgrAcceptReq, mgrAcceptRes, new MockFilterChain());
            assertThat(mgrAcceptRes.getStatus()).isEqualTo(403);
        });
    }
}
