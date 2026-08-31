package nl.invokedynamic.demo.availability;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(
        info = @Info(
                title = "Availability Service API",
                version = "1.0.0",
                description = "High-speed CQRS table availability search backed by Redis caching and event-driven invalidation."
        )
)
public class AvailabilityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AvailabilityServiceApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public org.springframework.cache.CacheManager cacheManager(org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        return org.springframework.data.redis.cache.RedisCacheManager.builder(connectionFactory).build();
    }
}
