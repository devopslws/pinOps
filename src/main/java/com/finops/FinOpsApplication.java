package com.finops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class FinOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinOpsApplication.class, args);
    }
}
