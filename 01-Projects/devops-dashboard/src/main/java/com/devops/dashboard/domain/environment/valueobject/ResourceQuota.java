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
public class ResourceQuota {
    
    private String cpuRequest;
    private String cpuLimit;
    private String memoryRequest;
    private String memoryLimit;
    
    public static ResourceQuota development() {
        return ResourceQuota.builder()
            .cpuRequest("500m")
            .cpuLimit("2000m")
            .memoryRequest("512Mi")
            .memoryLimit("2Gi")
            .build();
    }
    
    public static ResourceQuota experiment() {
        return ResourceQuota.builder()
            .cpuRequest("1000m")
            .cpuLimit("4000m")
            .memoryRequest("2Gi")
            .memoryLimit("8Gi")
            .build();
    }
}
