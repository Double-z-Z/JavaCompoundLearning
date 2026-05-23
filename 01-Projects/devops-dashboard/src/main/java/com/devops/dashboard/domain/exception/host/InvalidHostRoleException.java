package com.devops.dashboard.domain.exception.host;

import com.devops.dashboard.domain.exception.shared.SharedException;
import com.devops.dashboard.domain.host.HostRole;

import java.util.Set;

/**
 * 主机角色无效异常，当主机不具备操作所需的指定角色时抛出。
 *
 * <h3>触发条件</h3>
 * <ul>
 *   <li>尝试在非 {@link HostRole#LOADGEN} 角色的主机上执行压测任务</li>
 *   <li>尝试在非 {@link HostRole#TARGET} 角色的主机上部署服务</li>
 *   <li>尝试在非 {@link HostRole#MCP_HOST} 角色的主机上执行 MCP 命令</li>
 * </ul>
 *
 * <h3>MCP Error Code 映射</h3>
 * <p>映射为 {@code INVALID_HOST_ROLE} 错误码，HTTP 状态码 409（冲突）。
 *
 * <h3>用户提示信息</h3>
 * <p>"主机 {hostId} 当前角色为 {actualRoles}，不具备 {expectedRole} 所需的权限。
 * 请切换到具有该角色的主机后重试。"
 */
public class InvalidHostRoleException extends SharedException {

    /**
     * 角色校验失败的主机 ID
     *
     * <p>诊断作用：定位哪台主机的角色配置不符合预期，
     * 结合 {@link #expectedRole} 和 {@link #actualRoles} 可完整还原冲突现场。
     */
    private final String hostId;

    /**
     * 操作所要求的期望角色
     *
     * <p>诊断作用：表明当前业务操作需要哪种角色类型，
     * 用于判断是否需要为主机添加新角色或更换目标主机。
     */
    private final HostRole expectedRole;

    /**
     * 主机实际拥有的角色集合（可能为 null）
     *
     * <p>诊断作用：展示主机的实际角色配置，与 {@link #expectedRole} 对比
     * 可快速判断是角色缺失还是完全错误的主机选择。
     * 为 null 时表示调用方未提供实际角色信息（仅做了枚举名校验）。
     */
    private final Set<HostRole> actualRoles;

    /**
     * 构建完整信息的角色无效异常（推荐使用）
     *
     * <p>包含期望角色和实际角色集合，提供最完整的诊断信息。
     *
     * @param hostId       角色校验失败的主机 ID
     * @param expectedRole 操作要求的期望角色
     * @param actualRoles  主机实际拥有的角色集合
     */
    public InvalidHostRoleException(String hostId, HostRole expectedRole, Set<HostRole> actualRoles) {
        super(String.format("Host '%s' does not have required role '%s'. Actual roles: %s",
                hostId, expectedRole.name(), actualRoles));
        this.hostId = hostId;
        this.expectedRole = expectedRole;
        this.actualRoles = actualRoles;
    }

    /**
     * 构建简化版角色无效异常（仅枚举名校验）
     *
     * <p>适用于仅需校验角色名称是否合法的场景，不包含实际角色集合信息。
     * {@link #getActualRoles()} 将返回 null。
     *
     * @param hostId            角色校验失败的主机 ID
     * @param expectedRoleName 期望角色的名称（必须为有效的 {@link HostRole} 枚举名）
     */
    public InvalidHostRoleException(String hostId, String expectedRoleName) {
        super(String.format("Host '%s' does not have required role '%s'", hostId, expectedRoleName));
        this.hostId = hostId;
        this.expectedRole = HostRole.valueOf(expectedRoleName);
        this.actualRoles = null;
    }

    /**
     * 获取角色校验失败的主机 ID
     *
     * @return 主机标识符
     */
    public String getHostId() {
        return hostId;
    }

    /**
     * 获取操作所要求的期望角色
     *
     * @return 期望的 {@link HostRole} 枚举值
     */
    public HostRole getExpectedRole() {
        return expectedRole;
    }

    /**
     * 获取主机实际拥有的角色集合
     *
     * @return 角色集合，若使用简化构造函数创建则返回 null
     */
    public Set<HostRole> getActualRoles() {
        return actualRoles;
    }
}
