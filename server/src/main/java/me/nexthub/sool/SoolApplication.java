package me.nexthub.sool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SoolApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoolApplication.class, args);
    }
}
