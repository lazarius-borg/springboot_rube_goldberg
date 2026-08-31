package nl.invokedynamic.demo.customer.api;

import nl.invokedynamic.demo.customer.domain.CustomerProfileEntity;
import nl.invokedynamic.demo.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private CustomerService customerService;

    @BeforeEach
    void setUp() {
        Jwt dummyJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "sub-demo", "email", "alice@example.com", "given_name", "Alice", "family_name", "Customer")
        );

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return dummyJwt;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(customerService))
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void shouldReturnCustomerProfile() throws Exception {
        CustomerProfileEntity profile = new CustomerProfileEntity(
                UUID.randomUUID(), "sub-demo", "alice@example.com", "Alice", "Customer", "+3100", "ACTIVE", Instant.now(), Instant.now()
        );
        when(customerService.getOrCreateProfile(anyString(), anyString(), anyString(), anyString())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void shouldUpdateCustomerProfile() throws Exception {
        CustomerProfileEntity updated = new CustomerProfileEntity(
                UUID.randomUUID(), "sub-demo", "alice@example.com", "Alice", "Smith", "+319999", "ACTIVE", Instant.now(), Instant.now()
        );
        when(customerService.updateProfile(anyString(), anyString(), anyString(), anyString())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "firstName": "Alice", "lastName": "Smith", "phoneNumber": "+319999" }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.phoneNumber").value("+319999"));
    }
}
