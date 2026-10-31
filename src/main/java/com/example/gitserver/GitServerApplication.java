package com.example.gitserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitServerApplication.class, args);
    }
}
