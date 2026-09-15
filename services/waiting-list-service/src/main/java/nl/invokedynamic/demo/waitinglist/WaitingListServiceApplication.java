package nl.invokedynamic.demo.waitinglist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaitingListServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaitingListServiceApplication.class, args);
    }
}

