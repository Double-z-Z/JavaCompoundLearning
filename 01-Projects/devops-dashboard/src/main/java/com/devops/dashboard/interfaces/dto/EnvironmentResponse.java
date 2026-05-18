package com.devops.dashboard.interfaces.dto;

import com.devops.dashboard.domain.environment.EnvironmentStatus;
import com.devops.dashboard.domain.environment.EnvironmentType;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "环境响应")
public class EnvironmentResponse {

    @Schema(description = "环境ID")
    private String id;

    @Schema(description = "环境名称")
    private String name;

    @Schema(description = "环境类型")
    private EnvironmentType type;

    @Schema(description = "环境状态")
    private EnvironmentStatus status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "资源配额")
    private ResourceQuota resourceQuota;

    @Schema(description = "生命周期策略")
    private LifecyclePolicy lifecyclePolicy;

    @Schema(description = "访问端点")
    private Map<String, String> accessEndpoints;

    @Schema(description = "服务实例数量")
    private int serviceCount;

    public static EnvironmentResponse fromEntity(com.devops.dashboard.domain.environment.Environment env) {
        return EnvironmentResponse.builder()
                .id(env.getId().getValue())
                .name(env.getName())
                .type(env.getType())
                .status(env.getStatus())
                .createdAt(env.getCreatedAt())
                .resourceQuota(env.getResourceQuota())
                .lifecyclePolicy(env.getLifecyclePolicy())
                .accessEndpoints(env.getAccessEndpoints())
                .serviceCount(env.getServices() != null ? env.getServices().size() : 0)
                .build();
    }
}
