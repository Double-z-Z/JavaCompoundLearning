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
public class LifecyclePolicy {
    
    private boolean autoDestroy;
    private String maxLifetime;      // "24h", "2h"
    private String idleTimeout;       // "2h", "30m"
    private boolean destroyOnFailure;
    
    public static LifecyclePolicy defaultForDev() {
        return LifecyclePolicy.builder()
            .autoDestroy(false)
            .maxLifetime("24h")
            .idleTimeout("2h")
            .destroyOnFailure(true)
            .build();
    }
    
    public static LifecyclePolicy forExperiment() {
        return LifecyclePolicy.builder()
            .autoDestroy(true)
            .maxLifetime("2h")
            .idleTimeout("30m")
            .destroyOnFailure(true)
            .build();
    }
}
