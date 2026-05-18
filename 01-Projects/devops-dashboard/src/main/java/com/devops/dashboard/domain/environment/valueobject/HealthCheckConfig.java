package com.devops.dashboard.domain.environment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckConfig {
    
    private String endpoint;
    private int initialDelaySeconds;
    private int periodSeconds;
    private int timeoutSeconds;
    private int failureThreshold;
    private int successThreshold;
    
    public static HealthCheckConfig httpEndpoint(String endpoint) {
        return HealthCheckConfig.builder()
            .endpoint(endpoint)
            .initialDelaySeconds(30)
            .periodSeconds(10)
            .timeoutSeconds(5)
            .failureThreshold(3)
            .successThreshold(1)
            .build();
    }
    
    public static HealthCheckConfig command(String command) {
        return HealthCheckConfig.builder()
            .endpoint(command)
            .initialDelaySeconds(30)
            .periodSeconds(10)
            .timeoutSeconds(5)
            .failureThreshold(3)
            .successThreshold(1)
            .build();
    }
}
