package com.devops.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevOpsDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevOpsDashboardApplication.class, args);
        System.out.println("""
            
            ╔══════════════════════════════════════╗
            ║     DevOps Dashboard Started!       ║
            ║     http://localhost:8080           ║
            ║     Swagger UI: /swagger-ui.html   ║
            ╚══════════════════════════════════════╝
            
            """);
    }
}
