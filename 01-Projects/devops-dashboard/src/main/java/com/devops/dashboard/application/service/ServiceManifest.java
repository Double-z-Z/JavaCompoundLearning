package com.devops.dashboard.application.service;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 服务清单（用于部署时的配置）
 */
@Getter
@Builder
public class ServiceManifest {
    
    private final String templateName;  // 引用的模板名（如 "nacos-server"）
    
    private final String image;         // 镜像地址（覆盖默认值）
    
    private final Map<Integer, Integer> portMappings;  // 端口映射
    
    private final Map<String, String> environmentVariables;  // 环境变量
    
    private final List<String> dependsOn;  // 依赖的服务名
    
    private final ResourceOverride resourceOverride;  // 资源覆盖
    
    public static ServiceManifest fromTemplate(String templateName) {
        return ServiceManifest.builder()
            .templateName(templateName)
            .build();
    }
    
    public static ServiceManifestBuilder builder() {
        return new ServiceManifestBuilder();
    }
    
    @Getter
    @Builder
    public static class ResourceOverride {
        private final String cpuRequest;
        private final String cpuLimit;
        private final String memoryRequest;
        private final String memoryLimit;
    }
}
