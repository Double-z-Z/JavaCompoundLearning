package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.environment.valueobject.HealthCheckConfig;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class EnvironmentSpec {
    
    private final EnvironmentType type;
    private final List<TargetNodeRef> targetNodes;
    private final ResourceQuota resourceQuota;
    private final LifecyclePolicy lifecyclePolicy;
    private final Map<String, String> networkConfig;
    
    public static EnvironmentSpecBuilder builder() {
        return new EnvironmentSpecBuilder();
    }
    
    public static class EnvironmentSpecBuilder {
        // Builder implementation with defaults
    }
}
