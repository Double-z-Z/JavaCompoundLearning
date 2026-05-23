package com.devops.dashboard.application.host.impl;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.domain.exception.host.InvalidHostRoleException;
import com.devops.dashboard.domain.host.*;
import com.devops.dashboard.domain.loadgen.LoadgenTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link HostService} 的默认实现，编排主机领域对象完成拓扑构建与校验逻辑。
 *
 * <h2>mcpHostId 确定策略</h2>
 * <p>MCP（Model Context Protocol）主机 ID 用于标识当前运行 MCP Server 的宿主机器，
 * 在拓扑视图中作为<strong>锚点节点</strong>。确定优先级如下：</p>
 * <ol>
 *   <li><strong>配置驱动</strong>：读取 {@code devops.dashboard.mcp-host-id} 属性值
 *       （默认 {@code vm-fedora-dev}），若非空则直接使用。</li>
 *   <li><strong>角色推断</strong>：配置为空时，从仓储中查找具备
 *       {@link HostRole#MCP_HOST} 角色的第一台主机，取其 ID。</li>
 *   <li><strong>兜底值</strong>：以上均无匹配时返回字面量 {@code "unknown"}。</li>
 * </ol>
 *
 * <h2>校验逻辑总览</h2>
 * <table>
 *   <tr><th>方法</th><th>第一步</th><th>第二步</th><th>失败行为</th></tr>
 *   <tr><td>{@link #validateRole(HostId, HostRole)}</td>
 *       <td>findHostOrThrow</td><td>roles.contains()</td>
 *       <td>{@link InvalidHostRoleException}</td></tr>
 *   <tr><td>{@link #validateCapability(HostId, Capability)}</td>
 *       <td>findHostOrThrow</td><td>capabilities.contains()</td>
 *       <td>{@link HostCapabilityMismatchException}</td></tr>
 *   <tr><td>{@link #checkResourceAvailability(HostId, int, int)}</td>
 *       <td>findHostOrThrow</td><td>resources.canAccommodate()</td>
 *       <td>返回 false（resources 为 null 时宽松返回 true）</td></tr>
 *   <tr><td>{@link #getAvailableLoadgenTools(HostId)}</td>
 *       <td>findHostOrThrow</td><td>stream().map()</td>
 *       <td>N/A（返回空列表）</td></tr>
 *   <tr><td>{@link #getHostLabel(HostId)}</td>
 *       <td>findHostOrThrow</td><td>getLabel()</td>
 *       <td>N/A</td></tr>
 * </table>
 *
 * @see HostService
 * @see HostTopology
 */
@Service
public class HostServiceImpl implements HostService {

    private static final Logger log = LoggerFactory.getLogger(HostServiceImpl.class);

    private final HostRepository hostRepository;

    /**
     * MCP 宿主机的确定性 ID，由构造器注入的配置属性决定。
     *
     * <p>通过 {@code @Value("${devops.dashboard.mcp-host-id:vm-fedora-dev}")} 注入，
     * 默认值为 {@code "vm-fedora-dev"}。该字段在 Bean 生命周期内不可变，
     * 由 {@link #determineMcpHostId()} 方法消费以生成最终的锚点 ID。</p>
     */
    private final String mcpHostId;

    /**
     * 构造 HostServiceImpl，注入主机仓储和 MCP 主机 ID 配置。
     *
     * @param hostRepository 主机数据访问层实现，不允许为 null
     * @param mcpHostId      MCP 宿主机标识，来自 {@code devops.dashboard.mcp-host-id}
     *                       配置属性，默认 {@code "vm-fedora-dev"}
     */
    public HostServiceImpl(HostRepository hostRepository,
                           @Value("${devops.dashboard.mcp-host-id:vm-fedora-dev}") String mcpHostId) {
        this.hostRepository = hostRepository;
        this.mcpHostId = mcpHostId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现细节：调用 {@link #determineMcpHostId()} 解析锚点 ID，
     * 再从仓储获取全量主机列表组装 {@link HostTopology}。</p>
     */
    @Override
    public HostTopology getTopology() {
        List<Host> allHosts = hostRepository.findAll();
        log.debug("Generating topology with {} hosts", allHosts.size());
        return new HostTopology(determineMcpHostId(), allHosts);
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验流程：先通过 {@link #findHostOrThrow(HostId)} 确认主机存在，
     * 再检查其角色集合是否包含 {@code expectedRole}。
     * 异常消息中包含主机实际拥有的全部角色，便于排查。</p>
     */
    @Override
    public void validateRole(HostId hostId, HostRole expectedRole) {
        Host host = findHostOrThrow(hostId);
        if (!host.getRoles().contains(expectedRole)) {
            throw new InvalidHostRoleException(hostId.value(), expectedRole, host.getRoles());
        }
        log.debug("Role validation passed: {} has role {}", hostId.value(), expectedRole.name());
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验流程：先确认主机存在，再检查能力集合是否包含目标能力。
     * 与 {@link #validateRole(HostId, HostRole)} 结构一致，
     * 但抛出不同的异常类型以便调用方差异化处理。</p>
     */
    @Override
    public void validateCapability(HostId hostId, Capability capability) {
        Host host = findHostOrThrow(hostId);
        if (!host.getCapabilities().contains(capability)) {
            throw new HostCapabilityMismatchException(hostId.value(), capability, host.getCapabilities());
        }
        log.debug("Capability validation passed: {} has capability {}", hostId.value(), capability.name());
    }

    /**
     * {@inheritDoc}
     *
     * <p>特殊处理：若主机的 {@code resources} 字段为 null（YAML 中未配置），
     * 不视为资源不足，而是输出 WARN 日志并返回 {@code true}（宽松策略），
     * 避免因配置缺失阻断正常业务流。</p>
     */
    @Override
    public boolean checkResourceAvailability(HostId hostId, int requiredCpu, int requiredMemoryMb) {
        Host host = findHostOrThrow(hostId);
        Resources resources = host.getResources();
        if (resources == null) {
            log.warn("No resource information available for host: {}", hostId.value());
            return true;
        }
        boolean available = resources.canAccommodate(requiredCpu, requiredMemoryMb);
        log.debug("Resource check for {}: cpu={}, mem={}, available={}",
                hostId.value(), requiredCpu, requiredMemoryMb, available);
        return available;
    }

    /**
     * {@inheritDoc}
     *
     * <p>将领域对象 {@link LoadgenTool} 集合映射为其命令字符串表示，
     * 返回扁平化的字符串列表供外部消费者直接使用。</p>
     */
    @Override
    public List<String> getAvailableLoadgenTools(HostId hostId) {
        Host host = findHostOrThrow(hostId);
        return host.getLoadgenTools().stream()
                .map(LoadgenTool::getCommand)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHostLabel(HostId hostId) {
        Host host = findHostOrThrow(hostId);
        return host.getLabel();
    }

    /**
     * 根据 HostId 查询主机，不存在时抛出 {@link HostNotFoundException}。
     *
     * <p>这是所有接收 HostId 参数的公共方法的统一入口守卫，
     * 消除各方法中重复的「查询 → Optional 判断 → 抛异常」样板代码。</p>
     *
     * @param hostId 待查找的主机标识
     * @return 匹配的 Host 领域对象
     * @throws HostNotFoundException 当 hostId 在仓储中不存在时抛出
     */
    private Host findHostOrThrow(HostId hostId) {
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new HostNotFoundException(hostId.value()));
    }

    /**
     * 按「配置 → 角色推断 → 兜底」三级策略确定 MCP 宿主机 ID。
     *
     * <ol>
     *   <li>若构造器注入的 {@link #mcpHostId} 非空白，直接返回该值（最高优先级）。</li>
     *   <li>否则从仓储中查找第一个标记为 {@link HostRole#MCP_HOST} 的主机 ID。</li>
     *   <li>若仍无法确定，返回字面量 {@code "unknown"} 作为最终兜底。</li>
     * </ol>
     *
     * @return 确定后的 MCP 宿主机 ID 字符串，永不为 null
     */
    private String determineMcpHostId() {
        if (mcpHostId != null && !mcpHostId.isBlank()) {
            return mcpHostId;
        }
        List<Host> mcpHosts = hostRepository.findByRole(HostRole.MCP_HOST);
        if (!mcpHosts.isEmpty()) {
            return mcpHosts.get(0).getId().value();
        }
        return "unknown";
    }
}
