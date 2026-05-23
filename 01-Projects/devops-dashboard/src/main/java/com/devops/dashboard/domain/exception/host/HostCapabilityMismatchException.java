package com.devops.dashboard.domain.exception.host;

import com.devops.dashboard.domain.exception.shared.SharedException;
import com.devops.dashboard.domain.host.Capability;

import java.util.Set;

/**
 * 主机能力不匹配异常，当主机不具备操作所需的指定能力时抛出。
 *
 * <h3>触发条件</h3>
 * <ul>
 *   <li>尝试在不支持 Docker 的主机上创建容器化环境</li>
 *   <li>尝试在无 GPU 能力的主机上运行需要 GPU 的负载</li>
 *   <li>尝试在未安装 SSH 的主机上执行远程命令</li>
 * </ul>
 *
 * <h3>MCP Error Code 映射</h3>
 * <p>映射为 {@code HOST_CAPABILITY_MISMATCH} 错误码，HTTP 状态码 422（不可处理实体）。
 *
 * <h3>用户提示信息</h3>
 * <p>"主机 {hostId} 不支持 {requiredCapability} 能力（当前能力: {actualCapabilities}）。
 * 请选择具备该能力的主机，或为主机安装相应依赖后重试。"
 */
public class HostCapabilityMismatchException extends SharedException {

    /**
     * 能力校验失败的主机 ID
     *
     * <p>诊断作用：定位哪台主机的硬件/软件能力不符合要求，
     * 结合 {@link #requiredCapability} 可判断是缺少哪种依赖。
     */
    private final String hostId;

    /**
     * 操作所要求的期望能力
     *
     * <p>诊断作用：表明当前业务操作需要主机具备的特定能力类型，
     * 用于指导用户选择正确的主机或安装缺失的依赖。
     */
    private final Capability requiredCapability;

    /**
     * 主机实际拥有的能力集合（可能为 null）
     *
     * <p>诊断作用：展示主机的实际能力配置，与 {@link #requiredCapability} 对比
     * 可快速判断是完全缺失该能力还是能力集不完整。
     * 为 null 时表示调用方未提供实际能力信息（仅做了枚举名校验）。
     */
    private final Set<Capability> actualCapabilities;

    /**
     * 构建完整信息的能力不匹配异常（推荐使用）
     *
     * <p>包含期望能力和实际能力集合，提供最完整的诊断信息。
     *
     * @param hostId             能力校验失败的主机 ID
     * @param requiredCapability 操作要求的期望能力
     * @param actualCapabilities 主机实际拥有的能力集合
     */
    public HostCapabilityMismatchException(String hostId, Capability requiredCapability,
                                           Set<Capability> actualCapabilities) {
        super(String.format("Host '%s' does not support capability '%s'. Actual capabilities: %s",
                hostId, requiredCapability.name(), actualCapabilities));
        this.hostId = hostId;
        this.requiredCapability = requiredCapability;
        this.actualCapabilities = actualCapabilities;
    }

    /**
     * 构建简化版能力不匹配异常（仅枚举名校验）
     *
     * <p>适用于仅需校验能力名称是否合法的场景，不包含实际能力集合信息。
     * {@link #getActualCapabilities()} 将返回 null。
     *
     * @param hostId                  能力校验失败的主机 ID
     * @param requiredCapabilityName 期望能力的名称（必须为有效的 {@link Capability} 枚举名）
     */
    public HostCapabilityMismatchException(String hostId, String requiredCapabilityName) {
        super(String.format("Host '%s' does not support capability '%s'", hostId, requiredCapabilityName));
        this.hostId = hostId;
        this.requiredCapability = Capability.valueOf(requiredCapabilityName);
        this.actualCapabilities = null;
    }

    /**
     * 获取能力校验失败的主机 ID
     *
     * @return 主机标识符
     */
    public String getHostId() {
        return hostId;
    }

    /**
     * 获取操作所要求的期望能力
     *
     * @return 期望的 {@link Capability} 枚举值
     */
    public Capability getRequiredCapability() {
        return requiredCapability;
    }

    /**
     * 获取主机实际拥有的能力集合
     *
     * @return 能力集合，若使用简化构造函数创建则返回 null
     */
    public Set<Capability> getActualCapabilities() {
        return actualCapabilities;
    }
}
