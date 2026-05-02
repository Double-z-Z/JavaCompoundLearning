package com.example.counter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 计数器服务启动类
 */
@SpringBootApplication
public class CounterApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CounterApplication.class, args);
        System.out.println("Redis Counter Service Started!");
        System.out.println("API Endpoints:");
        System.out.println("  POST /counter/{key}/incr       - 自增 1");
        System.out.println("  POST /counter/{key}/incr/{n}   - 自增 n");
        System.out.println("  POST /counter/{key}/decr       - 自减 1");
        System.out.println("  GET  /counter/{key}            - 获取值");
        System.out.println("  POST /counter/{key}/set/{v}    - 设置值");
        System.out.println("  DELETE /counter/{key}          - 删除");
    }
}
