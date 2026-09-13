package nl.invokedynamic.demo.analytics.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Analytics Service API",
                version = "1.0.0",
                description = "Read-only business intelligence metrics aggregated asynchronously from Kafka domain events."
        )
)
public class OpenApiConfig {
}
