package nl.invokedynamic.demo.restaurant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/restaurants/**").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/restaurants/**").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").hasAnyRole("CUSTOMER", "RESTAURANT_MANAGER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    @Profile("docker")
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder multiIssuerJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:#{null}}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://keycloak:8080/realms/rube-goldberg}") String issuerUri,
            @Value("${security.jwt.accepted-issuers:http://localhost:8081/realms/rube-goldberg,http://keycloak:8080/realms/rube-goldberg}") List<String> acceptedIssuers) {
        String resolvedJwkSetUri = (jwkSetUri != null && !jwkSetUri.isBlank())
                ? jwkSetUri
                : issuerUri + "/protocol/openid-connect/certs";
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(resolvedJwkSetUri).build();
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> multiIssuerValidator = new JwtMultiIssuerValidator(acceptedIssuers);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, multiIssuerValidator));
        return jwtDecoder;
    }

    @Bean
    @Profile("!docker")
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder singleIssuerJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:#{null}}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8081/realms/rube-goldberg}") String issuerUri) {
        String resolvedJwkSetUri = (jwkSetUri != null && !jwkSetUri.isBlank())
                ? jwkSetUri
                : issuerUri + "/protocol/openid-connect/certs";
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(resolvedJwkSetUri).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return jwtDecoder;
    }
}
