package com.devops.dashboard.infrastructure.host;

import com.devops.dashboard.domain.host.*;
import com.devops.dashboard.domain.loadgen.LoadgenTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于 YAML 配置文件的 {@link Host} 仓储实现。
 *
 * <h2>加载策略</h2>
 * <p>采用<strong>启动时一次性加载 + 内存缓存</strong>策略：在 Spring 容器初始化阶段
 * （{@code @PostConstruct}）读取 classpath 下的 {@code hosts.yml}，解析全部主机定义后
 * 写入 {@link ConcurrentHashMap} 缓存。后续所有查询操作均从内存读取，无磁盘 I/O。</p>
 *
 * <h2>线程安全保证</h2>
 * <ul>
 *   <li><strong>写时点</strong>：仅在 {@link #init()} 阶段（单线程、容器启动期）执行写入，
 *       启动完成后缓存变为只读语义。</li>
 *   <li><strong>读时点</strong>：{@link #findById(HostId)}、{@link #findAll()} 等查询方法
 *       返回防御性副本（{@link List#.copyOf}），确保外部修改不影响缓存。</li>
 *   <li>{@link ConcurrentHashMap} 保证即使在极端情况下（如热重载扩展场景）的并发安全。</li>
 * </ul>
 *
 * <h2>YAML 格式约定（hosts.yml）</h2>
 * <pre>{@code
 * hosts:
 *   - id: "vm-fedora-dev"          # 必填，唯一标识
 *     type: "virtual_machine"      # 必填，对应 HostType 枚举 code
 *     parent: "phys-server-01"     # 可选，父节点 HostId，用于构建拓扑层级
 *     label: "Fedora 开发机"        # 必填，人类可读名称
 *     network_zone: "dev-lan"      # 可选，网络分区标识
 *     capabilities:                # 可选，能力标签列表
 *       - "ssh"
 *       - "docker"
 *     roles:                       # 可选，角色列表
 *       - "target"
 *       - "loadgen"
 *     resources:                   # 可选，资源快照
 *       cpu_total: 8
 *       cpu_free: 3
 *       mem_total_mb: 16384
 *       mem_free_mb: 8192
 *     access:                      # 可选，SSH 访问信息
 *       ssh: "192.168.1.100"
 *       port: 22
 *       user: "deploy"
 *       key_path: "/home/deploy/.ssh/id_rsa"
 *     loadgen_tools:               # 可选，已安装的压测工具
 *       - "wrk"
 *       - "ab"
 * }</pre>
 *
 * <h2>校验规则</h2>
 * <ol>
 *   <li>文件必须存在于 classpath 根路径，且包含顶层 {@code hosts} 键。</li>
 *   <li>每条记录的 {@code id}、{@code type}、{@code label} 为必填字段。</li>
 *   <li>{@code parent} 引用的父节点必须在同一文件中已定义（启动时交叉校验）。</li>
 * </ol>
 *
 * @see HostRepository
 * @see Host
 */
@Repository
public class YamlHostRepository implements HostRepository {

    private static final Logger log = LoggerFactory.getLogger(YamlHostRepository.class);

    private final Map<HostId, Host> hostCache = new ConcurrentHashMap<>();

    /**
     * Spring 容器初始化回调，触发 YAML 配置的一次性加载与缓存填充。
     *
     * <p>此方法在 Bean 属性注入完成后、Bean 可用前由框架自动调用，
     * 确保首次查询前数据已就绪。若 {@code hosts.yml} 缺失或格式非法，
     * 将抛出异常阻止应用启动（fail-fast 策略）。</p>
     */
    @PostConstruct
    public void init() {
        loadFromYaml();
    }

    /**
     * 从 classpath 的 {@code hosts.yml} 加载并解析全部主机定义。
     *
     * <p>解析流程：读取 InputStream → SnakeYAML 反序列化 → 逐条映射为 {@link Host}
     * → 写入缓存 → 校验父子引用完整性。任何步骤失败均抛出异常终止加载。</p>
     */
    private void loadFromYaml() {
        Yaml yaml = new Yaml();
        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream("hosts.yml");

        if (inputStream == null) {
            throw new IllegalStateException("hosts.yml not found in classpath");
        }

        Map<String, Object> root = yaml.load(inputStream);
        if (root == null || !root.containsKey("hosts")) {
            throw new IllegalStateException("Invalid hosts.yml: missing 'hosts' key");
        }

        List<Map<String, Object>> hostsList = castToList(root.get("hosts"));

        for (Map<String, Object> hostMap : hostsList) {
            Host host = parseHost(hostMap);
            hostCache.put(host.getId(), host);
        }

        validateParentReferences();

        log.info("Loaded {} hosts from hosts.yml", hostCache.size());
    }

    /**
     * 将单条 YAML 主机映射转换为领域对象 {@link Host}。
     *
     * @param map 解析后的 YAML 节点键值对，必含 {@code id/type/label} 字段
     * @return 构建完成的不可变 Host 实例
     * @throws IllegalArgumentException 当必填字段缺失时抛出
     */
    @SuppressWarnings("unchecked")
    private Host parseHost(Map<String, Object> map) {
        String id = getString(map, "id");
        String typeStr = getString(map, "type");
        String parentStr = getOptionalString(map, "parent");
        String label = getString(map, "label");
        String networkZone = getOptionalString(map, "network_zone");

        HostType type = HostType.fromCode(typeStr);
        HostId parentId = parentStr != null ? HostId.of(parentStr) : null;

        Set<Capability> capabilities = parseCapabilities(getOptionalList(map, "capabilities"));
        Set<HostRole> roles = parseRoles(getOptionalList(map, "roles"));
        Resources resources = parseResources(getOptionalMap(map, "resources"));
        HostAccess access = parseAccess(getOptionalMap(map, "access"));
        Set<LoadgenTool> loadgenTools = parseLoadgenTools(getOptionalList(map, "loadgen_tools"));

        return Host.builder()
                .id(HostId.of(id))
                .type(type)
                .parentId(parentId)
                .label(label)
                .networkZone(networkZone)
                .capabilities(capabilities)
                .roles(roles)
                .resources(resources)
                .access(access)
                .loadgenTools(loadgenTools)
                .build();
    }

    private Set<Capability> parseCapabilities(List<Object> list) {
        if (list == null || list.isEmpty()) return Set.of();
        return list.stream()
                .map(obj -> Capability.fromCode(obj.toString()))
                .collect(Collectors.toSet());
    }

    private Set<HostRole> parseRoles(List<Object> list) {
        if (list == null || list.isEmpty()) return Set.of();
        return list.stream()
                .map(obj -> HostRole.fromCode(obj.toString()))
                .collect(Collectors.toSet());
    }

    private Set<LoadgenTool> parseLoadgenTools(List<Object> list) {
        if (list == null || list.isEmpty()) return Set.of();
        return list.stream()
                .map(obj -> LoadgenTool.fromCommand(obj.toString()))
                .collect(Collectors.toSet());
    }

    private Resources parseResources(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;

        int cpuTotal = getIntOrDefault(map, "cpu_total", 0);
        int cpuFree = getIntOrDefault(map, "cpu_free", 0);
        int memTotalMb = getIntOrDefault(map, "mem_total_mb", 0);
        int memFreeMb = getIntOrDefault(map, "mem_free_mb", 0);

        return Resources.builder()
                .cpuTotal(cpuTotal)
                .cpuFree(cpuFree)
                .memTotalMb(memTotalMb)
                .memFreeMb(memFreeMb)
                .build();
    }

    private HostAccess parseAccess(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;

        String ssh = getString(map, "ssh");
        int port = getIntOrDefault(map, "port", 22);
        String user = getString(map, "user");
        String keyPath = getOptionalString(map, "key_path");

        return HostAccess.builder()
                .sshHost(ssh)
                .sshPort(port)
                .user(user)
                .keyPath(keyPath)
                .build();
    }

    /**
     * 校验所有主机的 {@code parent} 引用是否指向已存在的 HostId。
     *
     * <p>在全部主机加载完毕后执行一次性完整性检查，确保拓扑树不会出现悬空引用。
     * 若发现无效引用立即抛出异常，阻止应用启动。</p>
     *
     * @throws IllegalArgumentException 当某主机的 parentId 在缓存中不存在时抛出
     */
    private void validateParentReferences() {
        for (Host host : hostCache.values()) {
            if (host.getParentId() != null && !hostCache.containsKey(host.getParentId())) {
                throw new IllegalArgumentException(
                        "Invalid parent reference: " + host.getParentId() +
                                " for host " + host.getId()
                );
            }
        }
    }

    /**
     * 根据 ID 查询单台主机。
     *
     * @param id 主机标识，不允许为 null
     * @return 包含匹配主机的 Optional；若不存在则返回 {@link Optional#empty()}
     */
    @Override
    public Optional<Host> findById(HostId id) {
        return Optional.ofNullable(hostCache.get(id));
    }

    /**
     * 返回全部主机的防御性副本列表。
     *
     * <p>返回的列表与内部缓存解耦，外部修改不会影响仓储状态。</p>
     *
     * @return 所有已加载主机的不可变列表
     */
    @Override
    public List<Host> findAll() {
        return List.copyOf(hostCache.values());
    }

    /**
     * 按角色筛选主机列表。
     *
     * @param role 目标角色，不允许为 null
     * @return 具备该角色的所有主机
     */
    @Override
    public List<Host> findByRole(HostRole role) {
        return hostCache.values().stream()
                .filter(host -> host.getRoles().contains(role))
                .toList();
    }

    /**
     * 按类型筛选主机列表。
     *
     * @param type 目标类型，不允许为 null
     * @返回 该类型的所有主机
     */
    @Override
    public List<Host> findByType(HostType type) {
        return hostCache.values().stream()
                .filter(host -> host.getType() == type)
                .toList();
    }

    /**
     * 查询指定父节点的直接子主机列表。
     *
     * @param parentId 父节点 ID，不允许为 null
     * @return parentId 对应的所有子主机（仅一级深度）
     */
    @Override
    public List<Host> findChildren(HostId parentId) {
        return hostCache.values().stream()
                .filter(host -> parentId.equals(host.getParentId()))
                .toList();
    }

    /**
     * 判断指定 ID 的主机是否存在于缓存中。
     *
     * @param id 待判断的主机标识
     * @return 存在返回 true，否则 false
     */
    @Override
    public boolean existsById(HostId id) {
        return hostCache.containsKey(id);
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString().trim();
    }

    private String getOptionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : null;
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for '{}': {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getOptionalList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return List.of(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOptionalMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castToList(Object obj) {
        if (obj instanceof List) {
            return (List<T>) obj;
        }
        return List.of();
    }
}
