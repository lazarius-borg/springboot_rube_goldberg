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

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerValidationTest {

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

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CustomerController(customerService))
                .setCustomArgumentResolvers(jwtResolver)
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptValidCustomerProfileUpdate() throws Exception {
        CustomerProfileEntity entity = new CustomerProfileEntity(
                UUID.randomUUID(), "sub-demo", "alice@example.com", "Alice", "Smith", "+31612345678",
                "ACTIVE", Instant.now(), Instant.now()
        );
        when(customerService.updateProfile(anyString(), anyString(), anyString(), anyString())).thenReturn(entity);

        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Alice",
                        "lastName": "Smith",
                        "phoneNumber": "+31612345678"
                    }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.phoneNumber").value("+31612345678"));
    }

    @Test
    void shouldRejectBlankNames() throws Exception {
        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "   ",
                        "lastName": "",
                        "phoneNumber": "+31612345678"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItems("firstName", "lastName")));
    }

    @Test
    void shouldRejectNamesExceedingMaxLength() throws Exception {
        String longName = "A".repeat(51);

        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "%s",
                        "lastName": "%s",
                        "phoneNumber": "+31612345678"
                    }
                """.formatted(longName, longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItems("firstName", "lastName")));
    }

    @Test
    void shouldRejectInvalidPhoneNumberFormatAndLength() throws Exception {
        // Less than 5 chars
        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Alice",
                        "lastName": "Smith",
                        "phoneNumber": "123"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("phoneNumber"));

        // More than 25 chars
        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Alice",
                        "lastName": "Smith",
                        "phoneNumber": "%s"
                    }
                """.formatted("1".repeat(26))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("phoneNumber"));

        // Invalid characters
        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Alice",
                        "lastName": "Smith",
                        "phoneNumber": "INVALID-PHONE-$$$"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("phoneNumber"));
    }
}
