package com.devops.dashboard.application.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MCP 服务注册表（V3 新增）。
 *
 * <p>维护所有可部署服务的白名单，{@code env_deploy_service} 和 {@code deploy_pipeline}
 * 在执行前必须校验 serviceName 在此注册表中。</p>
 *
 * <h3>数据来源</h3>
 * <ul>
 *   <li>默认：编译时硬编码的 Set（见构造函数）</li>
 *   <li>可配置：通过 {@code application-mcp.yml} 的 {@code devops.mcp.service-registry} 覆盖</li>
 * </ul>
 *
 * <h3>校验时机</h3>
 * <ul>
 *   <li>{@code env_deploy_service}：部署前校验 serviceName</li>
 *   <li>{@code deploy_pipeline}：execute() 前校验 serviceName</li>
 * </ul>
 *
 * @see com.devops.dashboard.mcp.error.McpError#forServiceNotRegistered(String, Set)
 */
@Component
@ConfigurationProperties(prefix = "devops.mcp")
public class ServiceRegistry {

    /**
     * 默认注册服务（硬编码）。
     * 与 tools/list.json 中的 serviceName enum 保持同步。
     */
    private static final Set<String> DEFAULT_SERVICES = Set.of(
            "redis-counter-service",
            "devops-dashboard",
            "mcp-host-agent",
            "redis-cache"
    );

    /**
     * 可配置的服务注册列表（从 application-mcp.yml 注入）。
     * 如果为空，则使用 DEFAULT_SERVICES。
     */
    private Set<String> serviceRegistry = new LinkedHashSet<>();

    public ServiceRegistry() {
        // 默认注册表
        this.serviceRegistry.addAll(DEFAULT_SERVICES);
    }

    /**
     * 校验服务是否已注册。
     *
     * @param serviceName 服务名
     * @return true if registered
     */
    public boolean isRegistered(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return serviceRegistry.contains(serviceName);
    }

    /**
     * 获取所有可用服务。
     *
     * @return 不可变的已注册服务集合
     */
    public Set<String> getAvailableServices() {
        return Set.copyOf(serviceRegistry);
    }

    /**
     * 注册一个新服务（运行时动态添加）。
     *
     * @param serviceName 服务名
     * @return true if newly added, false if already existed
     */
    public boolean register(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return serviceRegistry.add(serviceName);
    }

    /**
     * 反注册一个服务（运行时动态移除）。
     *
     * @param serviceName 服务名
     * @return true if removed, false if not found
     */
    public boolean unregister(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return serviceRegistry.remove(serviceName);
    }

    /**
     * 获取注册表大小（用于测试/监控）。
     */
    public int size() {
        return serviceRegistry.size();
    }

    /**
     * 配置注入 setter（用于 YAML 配置绑定）。
     */
    public void setServiceRegistry(Set<String> serviceRegistry) {
        if (serviceRegistry != null && !serviceRegistry.isEmpty()) {
            this.serviceRegistry = new LinkedHashSet<>(serviceRegistry);
        }
    }
}