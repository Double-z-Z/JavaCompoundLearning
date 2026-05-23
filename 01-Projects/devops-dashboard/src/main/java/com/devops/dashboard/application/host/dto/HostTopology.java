package com.devops.dashboard.application.host.dto;

import com.devops.dashboard.domain.host.Capability;
import com.devops.dashboard.domain.host.Host;
import com.devops.dashboard.domain.host.HostRole;

import java.util.List;

/**
 * 主机拓扑数据传输对象，承载全量主机信息的序列化视图。
 *
 * <h2>JSON 序列化格式</h2>
 * <p>本类及内嵌 Record（{@link HostDto}、{@link ResourceDto}）设计为可直接
 * 由 Jackson 序列化为 JSON 的结构，典型输出如下：</p>
 * <pre>{@code
 * {
 *   "mcpHostId": "vm-fedora-dev",
 *   "hosts": [
 *     {
 *       "id": "vm-fedora-dev",
 *       "type": "virtual_machine",
 *       "parentId": "phys-server-01",
 *       "label": "Fedora 开发机",
 *       "networkZone": "dev-lan",
 *       "capabilities": ["ssh", "docker"],
 *       "roles": ["mcp_host", "target", "loadgen"],
 *       "resources": {
 *         "cpuTotal": 8,
 *         "cpuFree": 3,
 *         "memTotalMb": 16384,
 *         "memFreeMb": 8192,
 *         "cpuUtilizationPercent": 62.5,
 *         "memUtilizationPercent": 50.0
 *       },
 *       "isTarget": true,
 *       "isLoadgen": true,
 *       "isMcpHost": true,
 *       "loadgenTools": ["wrk", "ab"]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>与 MCP Resource 协议的对应关系</h2>
 * <p>本 DTO 直接服务于 MCP（Model Context Protocol）Server 的资源列表响应：
 * <ul>
 *   <li>{@link #getMcpHostId()} → 对应 MCP 资源的 {@code uri} 锚点前缀，
 *       标识当前 MCP Server 运行所在的主机。</li>
 *   <li>{@link #getHosts()} → 对应 MCP {@code resources/list} 工具返回的资源集合，
 *       每台主机映射为一个 Resource 条目。</li>
 *   <li>{@link HostDto#isMcpHost()} → 用于前端高亮标记 MCP 宿主节点。</li>
 *   <li>{@link HostDto#isTarget()} / {@link HostDto#isLoadgen()} →
 *       角色快捷判断字段，避免消费者自行解析 roles 列表。</li>
 * </ul>
 *
 * <h2>不可变性</h2>
 * <p>所有字段均为 {@code final}，通过构造器一次性赋值后不可变更。
 * 内嵌的 {@link HostDto} 和 {@link ResourceDto} 均为 Java Record，
 * 天然具备值语义和不可变性。</p>
 *
 * @see com.devops.dashboard.application.host.HostService#getTopology()
 */
public class HostTopology {

    private final String mcpHostId;
    private final List<HostDto> hosts;

    /**
     * 构建完整拓扑视图。
     *
     * @param mcpHostId MCP 宿主机 ID 锚点，不允许为 null
     * @param hosts     全部主机领域对象列表，将逐个转换为 {@link HostDto}
     */
    public HostTopology(String mcpHostId, List<Host> hosts) {
        this.mcpHostId = mcpHostId;
        this.hosts = hosts.stream()
                .map(HostDto::fromDomain)
                .toList();
    }

    /**
     * 获取 MCP 宿主机标识。
     *
     * <p>该值在 JSON 中作为顶层字段输出，前端据此定位拓扑树的根锚点。</p>
     *
     * @return MCP 宿主机 ID 字符串，永不为 null
     */
    public String getMcpHostId() {
        return mcpHostId;
    }

    /**
     * 获取全部主机的 DTO 视图列表。
     *
     * @return 不可变的 HostDto 列表，永不为 null（可能为空）
     */
    public List<HostDto> getHosts() {
        return hosts;
    }

    /**
     * 单台主机的序列化视图 Record，将领域对象 {@link Host} 展平为 JSON 友好的结构。
     *
     * <h3>字段说明</h3>
     * <table>
     *   <tr><th>字段</th><th>类型</th><th>来源</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>String</td><td>Host.id.value()</td><td>唯一机器标识</td></tr>
     *   <tr><td>{@code type}</td><td>String</td><td>HostType.getCode()</td><td>类型编码</td></tr>
     *   <tr><td>{@code parentId}</td><td>String?</td><td>Host.parentId.value()</td><td>父节点 ID，无父节点时为 null</td></tr>
     *   <tr><td>{@code label}</td><td>String</td><td>Host.getLabel()</td><td>人类可读名称</td></tr>
     *   <tr><td>{@code networkZone}</td><td>String?</td><td>Host.getNetworkZone()</td><td>网络分区</td></tr>
     *   <tr><td>{@code capabilities}</td><td>List&lt;String&gt;</td><td>Capability.getCode()</td><td>能力标签编码列表</td></tr>
     *   <tr><td>{@code roles}</td><td>List&lt;String&gt;</td><td>HostRole.getCode()</td><td>角色编码列表</td></tr>
     *   <tr><td>{@code resources}</td><td>ResourceDto?</td><td>Resources</td><td>资源快照，未配置时为 null</td></tr>
     *   <tr><td>{@code isTarget}</td><td>boolean</td><td>Host.isTarget()</td><td>是否为目标机角色</td></tr>
     *   <tr><td>{@code isLoadgen}</td><td>boolean</td><td>Host.isLoadgen()</td><td>是否为负载生成器角色</td></tr>
     *   <tr><td>{@code isMcpHost}</td><td>boolean</td><td>Host.isMcpHost()</td><td>是否为 MCP 宿主机角色</td></tr>
     *   <tr><td>{@code loadgenTools}</td><td>List&lt;String&gt;</td><td>LoadgenTool.getCommand()</td><td>已安装压测工具命令名列表</td></tr>
     * </table>
     *
     * <h3>MCP 协议映射</h3>
     * <p>本 Record 的每个实例对应一个 MCP Resource 条目：
     * {@code uri = "host://" + mcpHostId + "/" + id}，
     * {@code name = label}，{@code description = type + roles 摘要}。</p>
     */
    public record HostDto(
            String id,
            String type,
            String parentId,
            String label,
            String networkZone,
            List<String> capabilities,
            List<String> roles,
            ResourceDto resources,
            boolean isTarget,
            boolean isLoadgen,
            boolean isMcpHost,
            List<String> loadgenTools
    ) {
        /**
         * 将领域对象 {@link Host} 转换为传输用 DTO。
         *
         * <p>转换规则：枚举类型取其 code 字符串、引用类型取 value() 或直接映射、
         * 集合类型逐元素转换。parentId 和 resources 在原对象为 null 时映射为 null。</p>
         *
         * @param host 源领域对象，不允许为 null
         * @return 填充完成的 HostDto 实例
         */
        public static HostDto fromDomain(Host host) {
            return new HostDto(
                    host.getId().value(),
                    host.getType().getCode(),
                    host.getParentId() != null ? host.getParentId().value() : null,
                    host.getLabel(),
                    host.getNetworkZone(),
                    host.getCapabilities().stream().map(Capability::getCode).toList(),
                    host.getRoles().stream().map(HostRole::getCode).toList(),
                    host.getResources() != null ?
                            ResourceDto.fromDomain(host.getResources()) : null,
                    host.isTarget(),
                    host.isLoadgen(),
                    host.isMcpHost(),
                    host.getLoadgenTools().stream()
                            .map(com.devops.dashboard.domain.loadgen.LoadgenTool::getCommand)
                            .toList()
            );
        }
    }

    /**
     * 主机资源快照的序列化视图 Record。
     *
     * <h3>字段说明</h3>
     * <table>
     *   <tr><th>字段</th><th>类型</th><th>单位</th><th>说明</th></tr>
     *   <tr><td>{@code cpuTotal}</td><td>int</td><td>核心数</td><td>CPU 总量</td></tr>
     *   <tr><td>{@code cpuFree}</td><td>int</td><td>核心数</td><td>CPU 空闲量</td></tr>
     *   <tr><td>{@code memTotalMb}</td><td>int</td><td>MB</td><td>内存总量</td></tr>
     *   <tr><td>{@code memFreeMb}</td><td>int</td><td>MB</td><td>内存空闲量</td></tr>
     *   <tr><td>{@code cpuUtilizationPercent}</td><td>double</td><td>%</td><td>CPU 使用率（0~100），由领域对象计算得出</td></tr>
     *   <tr><td>{@code memUtilizationPercent}</td><td>double</td><td>%</td><td>内存使用率（0~100），由领域对象计算得出</td></tr>
     * </table>
     *
     * <p>利用率字段为<strong>派生值</strong>：{@code utilization = (total - free) / total * 100}，
     * 计算逻辑封装在领域层 {@code Resources} 对象中，DTO 仅负责透传。</p>
     */
    public record ResourceDto(
            int cpuTotal,
            int cpuFree,
            int memTotalMb,
            int memFreeMb,
            double cpuUtilizationPercent,
            double memUtilizationPercent
    ) {
        /**
         * 将领域资源对象转换为传输用 DTO。
         *
         * @param resources 源领域资源对象，不允许为 null
         * @return 包含绝对量和派生利用率的 ResourceDto 实例
         */
        public static ResourceDto fromDomain(com.devops.dashboard.domain.host.Resources resources) {
            return new ResourceDto(
                    resources.getCpuTotal(),
                    resources.getCpuFree(),
                    resources.getMemTotalMb(),
                    resources.getMemFreeMb(),
                    resources.cpuUtilizationPercent(),
                    resources.memUtilizationPercent()
            );
        }
    }
}
