package com.example.counter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WebFlux 版本启动类
 *
 * 注意：WebFlux 使用 Netty 作为默认容器，不需要配置 Tomcat
 */
@SpringBootApplication
public class WebFluxCounterApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebFluxCounterApplication.class, args);
    }
}