package com.smartfactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class SmartFactoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFactoryApplication.class, args);
    }
}
