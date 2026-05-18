package com.devops.dashboard.domain.environment;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "service_instances", indexes = {
    @Index(name = "idx_svc_instance_id", columnList = "instanceId", unique = true),
    @Index(name = "idx_svc_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String instanceId;
    
    private String serviceTemplate;
    
    @Enumerated(EnumType.STRING)
    private ServiceInstanceStatus status;
    
    // 配置信息（JSON存储）
    @Column(columnDefinition = "TEXT")
    private String image;
    
    @ElementCollection
    @CollectionTable(name = "svc_ports", joinColumns = @JoinColumn(name = "svc_instance_id"))
    private List<PortMapping> ports = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "svc_env_vars", joinColumns = @JoinColumn(name = "svc_instance_id"))
    @MapKeyColumn(name = "var_name")
    @Column(name = "var_value")
    private Map<String, String> environmentVariables = new HashMap<>();
    
    @Embedded
    private HealthCheckConfig healthCheckConfig;
    
    // 运行时信息
    private String containerId;
    private LocalDateTime startedAt;
    private Double cpuPercent;
    private Long memoryMb;
    
    // 关联
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;
    
    public static ServiceInstance create(String template, String image) {
        var svc = new ServiceInstance();
        svc.instanceId = "svc-" + UUID.randomUUID().toString().substring(0, 8);
        svc.serviceTemplate = template;
        svc.image = image;
        svc.status = ServiceInstanceStatus.PENDING;
        return svc;
    }
    
    public void markAsDeploying() {
        this.status = ServiceInstanceStatus.DEPLOYING;
    }
    
    public void markAsRunning(String containerId) {
        this.status = ServiceInstanceStatus.RUNNING;
        this.containerId = containerId;
        this.startedAt = LocalDateTime.now();
    }
    
    public void markAsStopped() {
        this.status = ServiceInstanceStatus.STOPPED;
    }
    
    public void markAsFailed(String reason) {
        this.status = ServiceInstanceStatus.FAILED;
    }
    
    public void updateResourceUsage(double cpuPercent, long memoryMb) {
        this.cpuPercent = cpuPercent;
        this.memoryMb = memoryMb;
    }
    
    // Package-private setter for JPA
    void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}

@AllArgsConstructor
@Getter
enum ServiceInstanceStatus {
    PENDING("等待中"),
    DEPLOYING("部署中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    FAILED("失败");
    
    private final String description;
}

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PortMapping {
    private int containerPort;
    private Integer hostPort;
    private String protocol;  // tcp | udp
}
