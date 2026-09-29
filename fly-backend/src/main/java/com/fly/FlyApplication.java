package com.fly;

import com.fly.config.FlyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(FlyProperties.class)
@EnableScheduling
public class FlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlyApplication.class, args);
    }
}
