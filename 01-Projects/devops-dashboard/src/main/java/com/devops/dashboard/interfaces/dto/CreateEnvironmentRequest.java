package com.devops.dashboard.interfaces.dto;

import com.devops.dashboard.domain.environment.EnvironmentType;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建环境请求体")
public class CreateEnvironmentRequest {

    @NotBlank(message = "环境名称不能为空")
    @Schema(
        description = "环境唯一标识名（建议格式：<类型>-<用途>）",
        example = "dev-nacos",
        requiredMode = Schema.RequiredMode.REQUIRED,
        allowableValues = {"dev-nacos", "test-mysql", "staging-redis", "prod-api"}
    )
    private String name;

    @NotNull(message = "环境类型不能为空")
    @Schema(
        description = """
            环境类型（决定资源配额默认值和管理策略）
            - DEV: 开发环境，低配额，适合日常开发调试
            - TEST: 测试环境，中等配额，用于功能测试
            - STAGING: 预发布环境，接近生产配置，上线前验证
            - PROD: 生产环境，高配额，正式对外服务（慎用！）
            - EXPERIMENT: 实验环境，自动销毁机制，用于Spike验证
            """,
        example = "DEV",
        requiredMode = Schema.RequiredMode.REQUIRED,
        allowableValues = {"DEV", "TEST", "STAGING", "PROD", "EXPERIMENT"}
    )
    private EnvironmentType type;

    @Schema(
        description = """
            资源配额限制（CPU/内存上限）。不填则使用该类型的默认值：
            
            | 类型 | 默认 CPU | 默认内存 |
            |------|----------|---------|
            | DEV | 500m~2000m | 512Mi~2Gi |
            | TEST | 1000m~4000m | 2Gi~8Gi |
            | PROD | 2000m~8000m | 4Gi~16Gi |
            | EXPERIMENT | 1000m~4000m | 2Gi~8Gi |
            
            单位说明：
            - CPU: millicores (1000m = 1核)
            - 内存: Mi (1024Mi = 1GB) 或 Gi
            
            示例：cpuRequest="500m" 表示请求 0.5 核 CPU
            """,
        implementation = ResourceQuota.class
    )
    private ResourceQuota resourceQuota;

    @Schema(
        description = """
            生命周期管理策略（控制环境存活时间和清理行为）
            
            关键字段说明：
            - autoDestroy: 是否自动销毁（实验环境建议开启）
            - maxLifetime: 最大存活时间（如"24h"=24小时，"2h"=2小时）
            - idleTimeout: 空闲超时时间（无操作多长时间后触发警告）
            - destroyOnFailure: 创建失败时是否自动清理
            
            推荐配置：
            | 场景 | autoDestroy | maxLifetime | idleTimeout |
            |------|------------|-------------|------------|
            | 开发环境 | false | 24h | 2h |
            | 实验环境 | true | 2h | 30m |
            | 生产环境 | false | 永久 | 永久 |
            """,
        implementation = LifecyclePolicy.class
    )
    private LifecyclePolicy lifecyclePolicy;

    @Builder.Default
    @Schema(
        description = """
            目标部署节点列表（指定环境运行在哪些机器上）
            
            适用场景：
            - 多机部署：将服务分布到不同物理机提高可用性
            - 指定机器：利用特定机器的 GPU/SSD 等特殊资源
            - 环境隔离：开发/测试环境分开部署避免干扰
            
            字段说明：
            - nodeId: 节点唯一标识（如 hostname 或 IP 别名）
            - ip: 节点 IP 地址（必须可从 Dashboard 主机访问）
            - role: 节点角色（primary=主节点, secondary=备用节点）
            
            注意：当前版本仅记录信息，实际调度由 Phase 2 的 DockerComposeProvider 实现
            """
    )
    private java.util.List<TargetNodeDTO> targetNodes = new java.util.ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TargetNodeDTO {
        
        @Schema(description = "节点标识符（建议使用主机名或自定义ID）", example = "node-worker-01")
        private String nodeId;
        
        @Schema(description = "节点 IP 地址（内网IP，如 192.168.1.100）", example = "192.168.1.100", format = "ipv4")
        private String ip;
        
        @Schema(
            description = "节点角色",
            example = "primary",
            allowableValues = {"primary", "secondary"}
        )
        private String role;
    }
}
