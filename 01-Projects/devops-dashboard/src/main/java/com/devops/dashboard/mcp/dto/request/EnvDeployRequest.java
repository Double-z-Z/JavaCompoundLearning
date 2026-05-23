package com.devops.dashboard.mcp.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 服务部署请求 DTO。
 *
 * <p>封装 MCP Tool {@code env_deploy_service} 的输入参数，
 * 用于向已有环境部署新的服务实例。</p>
 *
 * <h3>使用场景</h3>
 * <pre>
 * 用户: "部署 Nacos 到刚才的环境"
 * AI: 调用 env_deploy_service(envId="exp-xxx", templateName="nacos-server")
 * </pre>
 *
 * @see com.devops.dashboard.mcp.handler.EnvironmentHandler#envDeployService(EnvDeployRequest)
 */
@Getter
@Builder
public class EnvDeployRequest {

    /** 目标环境 ID */
    private final String envId;

    /** 服务模板名称（对应 service-templates.yml 中的模板 ID） */
    private final String templateName;

    /** 镜像名称/标签（覆盖模板默认值） */
    private final String image;

    /** 环境变量覆盖（key-value 对） */
    private final Map<String, String> envOverrides;
}
