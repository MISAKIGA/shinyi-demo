package com.shinyi.eventbus.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class EventBusDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventBusDemoApplication.class, args);
    }
}
