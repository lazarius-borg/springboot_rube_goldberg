package nl.invokedynamic.demo.waitinglist;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
        info = @Info(
                title = "Waiting List Service API",
                version = "1.0.0",
                description = "Fair FIFO waiting list matchmaker, 15-minute time-limited offers, and automated cascade lifecycle."
        )
)
public class WaitingListServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaitingListServiceApplication.class, args);
    }
}

