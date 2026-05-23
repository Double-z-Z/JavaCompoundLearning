package com.devops.dashboard.domain.exception.mcp;

import com.devops.dashboard.domain.exception.shared.SharedException;

import java.util.Set;

/**
 * 服务未注册异常（V3 新增）。
 *
 * <p>当 {@code env_deploy_service} 或 {@code deploy_pipeline} 尝试部署未在
 * {@link com.devops.dashboard.application.mcp.ServiceRegistry} 中注册的服务时抛出。</p>
 *
 * @see com.devops.dashboard.application.mcp.ServiceRegistry
 * @see com.devops.dashboard.mcp.error.McpError#forServiceNotRegistered(String, Set)
 */
public class ServiceNotRegisteredException extends SharedException {

    private final String serviceName;
    private final Set<String> availableServices;

    public ServiceNotRegisteredException(String serviceName, Set<String> availableServices) {
        super(String.format("服务 '%s' 未在 MCP 目录注册", serviceName));
        this.serviceName = serviceName;
        this.availableServices = availableServices;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Set<String> getAvailableServices() {
        return availableServices;
    }
}