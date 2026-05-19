package com.devops.dashboard.domain.environment;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 目标节点引用
 *
 * 表示环境中部署服务的主机节点信息，包含：
 * - nodeId: 节点唯一标识
 * - ip: 节点IP地址
 * - role: 节点角色（primary/replica/worker）
 *
 * @see EnvironmentSpec#targetNodes
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetNodeRef {
    private String nodeId;
    private String ip;
    private String role;
}
