package com.devops.dashboard.infrastructure.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger API 文档配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI devOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevOps Dashboard API")
                        .description("DevOps控制面板 - 环境管理、实验平台、流水线编排")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("DevOps Team")
                                .email("devops@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}