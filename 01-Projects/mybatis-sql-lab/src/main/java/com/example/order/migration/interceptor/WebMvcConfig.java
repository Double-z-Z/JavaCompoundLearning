package com.example.order.migration.interceptor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "migration.active", havingValue = "true")
public class WebMvcConfig implements WebMvcConfigurer {

    private final MigrationContextInterceptor migrationInterceptor;

    public WebMvcConfig(MigrationContextInterceptor migrationInterceptor) {
        this.migrationInterceptor = migrationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(migrationInterceptor);
    }
}
