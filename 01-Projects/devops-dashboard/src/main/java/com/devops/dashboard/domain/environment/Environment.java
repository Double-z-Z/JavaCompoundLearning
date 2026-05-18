package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.environment.valueobject.HealthCheckConfig;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "environments", indexes = {
    @Index(name = "idx_env_status", columnList = "status"),
    @Index(name = "idx_env_type", columnList = "type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Environment {
    
    @EmbeddedId
    private EnvironmentId id;
    
    private String name;
    
    @Enumerated(EnumType.STRING)
    private EnvironmentType type;
    
    @Enumerated(EnumType.STRING)
    private EnvironmentStatus status;
    
    private LocalDateTime createdAt;
    
    @ElementCollection
    @CollectionTable(name = "env_access_endpoints", joinColumns = @JoinColumn(name = "env_id"))
    @MapKeyColumn(name = "endpoint_name")
    @Column(name = "endpoint_url")
    private Map<String, String> accessEndpoints = new HashMap<>();
    
    // === 值对象 ===
    @Embedded
    private ResourceQuota resourceQuota;
    
    @Embedded
    private LifecyclePolicy lifecyclePolicy;
    
    // === 目标节点 ===
    @ElementCollection
    @CollectionTable(name = "env_target_nodes", joinColumns = @JoinColumn(name = "env_id"))
    private List<TargetNode> targetNodes = new ArrayList<>();
    
    // === 服务实例（实体）===
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ServiceInstance> services = new ArrayList<>();
    
    public static Environment create(String name, EnvironmentSpec spec) {
        var env = new Environment();
        env.id = EnvironmentId.generate();
        env.name = name;
        env.type = spec.getType();
        env.status = EnvironmentStatus.CREATING;
        env.createdAt = LocalDateTime.now();
        env.resourceQuota = spec.getResourceQuota() != null ? spec.getResourceQuota() : ResourceQuota.development();
        env.lifecyclePolicy = spec.getLifecyclePolicy() != null ? spec.getLifecyclePolicy() : LifecyclePolicy.defaultForDev();
        env.targetNodes = new ArrayList<>(spec.getTargetNodes());
        
        return env;
    }
    
    public void markAsRunning(Map<String, String> endpoints) {
        validateTransition(EnvironmentStatus.RUNNING);
        this.status = EnvironmentStatus.RUNNING;
        this.accessEndpoints.putAll(endpoints);
    }
    
    public void markAsStopped() {
        validateTransition(EnvironmentStatus.STOPPED);
        this.status = EnvironmentStatus.STOPPED;
    }
    
    public void markAsDestroyed() {
        validateTransition(EnvironmentStatus.DESTROYED);
        this.status = EnvironmentStatus.DESTROYED;
    }
    
    public void markAsFailed(String reason) {
        validateTransition(EnvironmentStatus.FAILED);
        this.status = EnvironmentStatus.FAILED;
    }
    
    public void addService(ServiceInstance service) {
        if (this.services == null) {
            this.services = new ArrayList<>();
        }
        this.services.add(service);
        service.setEnvironment(this);
    }
    
    public Optional<ServiceInstance> findServiceByInstanceId(String instanceId) {
        return services.stream()
            .filter(s -> s.getInstanceId().equals(instanceId))
            .findFirst();
    }
    
    private void validateTransition(EnvironmentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", this.status, target)
            );
        }
    }
}

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TargetNode {
    private String nodeId;
    private String ip;
    private String role;  // primary | secondary
}
