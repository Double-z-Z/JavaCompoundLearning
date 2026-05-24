package com.devops.dashboard.application.host;

import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.domain.host.*;

import java.util.List;

/**
 * 主机应用服务接口，提供主机拓扑查询、角色/能力校验、资源可用性检查等业务能力。
 *
 * <p>本接口位于<strong>应用层（Application Layer）</strong>，是领域模型与外部消费者
 * （REST Controller / MCP Tool）之间的编排入口。所有方法均以业务语义命名，
 * 内部封装了「查询 → 校验 → 异常翻译」的标准流程。</p>
 *
 * <h2>设计约束</h2>
 * <ul>
 *   <li><strong>无状态</strong>：实现类不应持有可变状态，所有数据来自 {@link com.devops.dashboard.domain.host.HostRepository}。</li>
 *   <li><strong>快速失败</strong>：校验方法在条件不满足时直接抛出业务异常，
 *       调用方无需自行检查返回值。</li>
 *   <li><strong>主机不存在 = 异常</strong>：除 {@link #getTopology()} 外，所有接收
 *       {@link HostId} 参数的方法在主机不存在时抛出
 *       {@link com.devops.dashboard.domain.exception.host.HostNotFoundException}。</li>
 * </ul>
 *
 * @see HostServiceImpl
 * @see HostTopology
 */
public interface HostService {

    /**
     * 构建当前环境的完整主机拓扑视图。
     *
     * <p>返回的拓扑包含全部已注册主机的层级关系、资源快照和角色标注，
     * 用于前端可视化展示或 MCP Resource 协议响应。调用前无需任何前置条件。</p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>Dashboard 首页加载时获取全量拓扑树</li>
     *   <li>MCP Server 响应 {@code resources/list} 请求</li>
     *   <li>运维审计导出主机清单</li>
     * </ul>
     *
     * @return 包含 mcpHostId 锚点和完整主机列表的不可变拓扑对象，永不为 null
     */
    HostTopology getTopology();

    /**
     * 断言指定主机具备预期角色，用于操作前的权限门控。
     *
     * <p>当主机不具备 {@code expectedRole} 时，抛出
     * {@link com.devops.dashboard.domain.exception.host.InvalidHostRoleException}。
     * 典型场景：执行压测前确认目标机具备 {@link HostRole#TARGET} 角色。</p>
     *
     * <h3>前置条件</h3>
     * <ul>
     *   <li>{@code hostId} 对应的主机必须存在于仓储中</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>压测任务启动前校验目标机是否为 target 角色</li>
     *   <li>部署流水线中确认部署目标角色正确</li>
     *   <li>MCP Tool 执行前的角色鉴权</li>
     * </ul>
     *
     * @param hostId          待校验的主机标识，不允许为 null
     * @param expectedRole    期望的角色值，不允许为 null
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException  主机不存在时抛出
     * @throws com.devops.dashboard.domain.exception.host.InvalidHostRoleException 角色不匹配时抛出
     */
    void validateRole(HostId hostId, HostRole expectedRole);

    /**
     * 断言指定主机具备指定能力标签，用于功能可用性前置校验。
     *
     * <p>当主机未声明该能力时，抛出
     * {@link com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException}。
     * 能力（Capability）比角色更细粒度，例如 {@code SSH}、{@code DOCKER} 等。</p>
     *
     * <h3>前置条件</h3>
     * <ul>
     *   <li>{@code hostId} 对应的主机必须存在于仓储中</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>执行 SSH 命令前确认主机具备 {@code SSH} 能力</li>
     *   <li>Docker 操作前确认主机支持容器运行时</li>
     *   <li>动态隐藏 UI 中不支持的功能按钮</li>
     * </ul>
     *
     * @param hostId      待校验的主机标识，不允许为 null
     * @param capability  期望的能力标签，不允许为 null
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException           主机不存在时抛出
     * @throws com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException 能力不匹配时抛出
     */
    void validateCapability(HostId hostId, Capability capability);

    /**
     * 检查指定主机的剩余资源是否能满足给定需求量。
     *
     * <p>基于 YAML 中配置的资源快照（cpu_free / mem_free_mb）进行静态容量判断，
     * 不进行实时探测。若主机未配置资源信息（resources 为 null），默认返回
     * {@code true}（宽松策略），并输出 WARN 日志提醒补充配置。</p>
     *
     * <h3>前置条件</h3>
     * <ul>
     *   <li>{@code hostId} 对应的主机必须存在于仓储中</li>
     *   <li>{@code requiredCpu} 和 {@code requiredMemoryMb} 应为非负整数</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>压测调度器选择负载生成器时的容量过滤</li>
     *   <li>多任务并发时的资源竞争预检</li>
     *   <li>前端展示主机健康度指标</li>
     * </ul>
     *
     * @param hostId            目标主机标识
     * @param requiredCpu       所需 CPU 核心数（空闲）
     * @param requiredMemoryMb  所需内存 MB（空闲）
     * @return 资源充足返回 true；不足或无法判断时返回 false（resources 为 null 时返回 true）
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException 主机不存在时抛出
     */
    boolean checkResourceAvailability(HostId hostId, int requiredCpu, int requiredMemoryMb);

    /**
     * 获取指定主机上已安装的全部压测工具命令名列表。
     *
     * <p>返回值为工具的可执行命令字符串（如 {@code "wrk"}、{@code "ab"}），
     * 可直接用于构建 Shell 命令。空列表表示该主机未配置任何压测工具。</p>
     *
     * <h3>前置条件</h3>
     * <ul>
     *   <li>{@code hostId} 对应的主机必须存在于仓储中</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>MCP Tool 列出可选压测工具供用户选择</li>
     *   <li>压测任务创建时校验工具名称合法性</li>
     *   <li>UI 下拉框动态填充可用工具选项</li>
     * </ul>
     *
     * @param hostId 目标主机标识
     * @return 已安装工具的命令名列表，永不为 null（可能为空列表）
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException 主机不存在时抛出
     */
    List<String> getAvailableLoadgenTools(HostId hostId);

    /**
     * 获取指定主机的可读标签（label）。
     *
     * <p>Label 是 YAML 中配置的人类友好名称（如 "Fedora 开发机"），
     * 与机器可读的 {@code id} 形成双轨制标识体系。</p>
     *
     * <h3>前置条件</h3>
     * <ul>
     *   <li>{@code hostId} 对应的主机必须存在于仓储中</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>日志输出中展示友好名称而非内部 ID</li>
     *   <li>错误消息中给出人类可读的主机指代</li>
     *   <li>UI 列表展示列</li>
     * </ul>
     *
     * @param hostId 目标主机标识
     * @return 主机的 label 字符串，永不为 null
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException 主机不存在时抛出
     */
    String getHostLabel(HostId hostId);

    /**
     * 获取指定主机的 SSH 访问信息。
     *
     * @param hostId 目标主机标识
     * @return 主机的 HostAccess 值对象
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException 主机不存在时抛出
     */
    HostAccess getHostAccess(HostId hostId);
}
