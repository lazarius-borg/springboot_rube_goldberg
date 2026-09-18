package nl.invokedynamic.demo.restaurant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    @Bean
    public RestClient restClient(
        @Value("${services.reservation-service.uri:http://localhost:8085}") String reservationServiceUri,
        final RestClient.Builder restClientBuilder) {
        return restClientBuilder.baseUrl(reservationServiceUri).build();
    }
}
