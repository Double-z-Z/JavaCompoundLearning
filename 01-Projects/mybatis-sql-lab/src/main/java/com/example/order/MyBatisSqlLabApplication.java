package com.example.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.order.infrastructure.persistence.sharding")
public class MyBatisSqlLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyBatisSqlLabApplication.class, args);
    }
}
