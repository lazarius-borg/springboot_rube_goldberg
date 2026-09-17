package nl.invokedynamic.demo.notification.api;

import nl.invokedynamic.demo.notification.service.CustomerSseEmitterService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationSseControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private CustomerSseEmitterService emitterService;

    private Jwt dummyJwt;

    @BeforeEach
    void setUp() {
        dummyJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "c7128e4e-0a56-43b8-89c5-7f2834789b12", "email", "alice@example.com")
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

        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationSseController(emitterService))
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void testSubscribeToStream() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(emitterService.register(anyString(), anyString())).thenReturn(emitter);

        MvcResult result = mockMvc.perform(get("/api/v1/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        verify(emitterService).register(eq("c7128e4e-0a56-43b8-89c5-7f2834789b12"), eq("alice@example.com"));
    }

    @Test
    void testSubscribeWithExplicitCustomerId() throws Exception {
        UUID customId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        when(emitterService.register(eq(customId.toString()), eq("alice@example.com"))).thenReturn(emitter);

        MvcResult result = mockMvc.perform(get("/api/v1/notifications/stream")
                        .param("customerId", customId.toString())
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        verify(emitterService).register(eq(customId.toString()), eq("alice@example.com"));
    }
}
