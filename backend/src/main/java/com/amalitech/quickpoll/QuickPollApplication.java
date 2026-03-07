package com.amalitech.quickpoll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuickPollApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickPollApplication.class, args);
    }
}
