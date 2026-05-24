package com.devops.dashboard.mcp.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * 环境创建请求 DTO（Data Transfer Object）。
 *
 * <p>封装 MCP Tool {@code env_create} 的输入参数，由 AI 根据用户意图构建后
 * 传递给 {@link com.devops.dashboard.mcp.handler.EnvironmentHandler} 处理。</p>
 *
 * <h3>参数约束</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>说明</th><th>示例</th></tr>
 *   <tr><td>{@code name}</td><td>可选</td><td>环境名称，为空时自动生成</td><td>"nacos-perf-test"</td></tr>
 *   <tr><td>{@code hostId}</td><td>推荐</td><td>目标主机ID（需在 hosts.yml 中注册）</td><td>"vm-ubuntu-test"</td></tr>
 *   <tr><td>{@code environmentType}</td><td>必填</td><td>环境类型</td><td>"EXPERIMENT" / "TEST" / "DEV"</td></tr>
 *   <tr><td>{@code isolationType}</td><td>可选</td><td>隔离类型，默认 DOCKER</td><td>"DOCKER" / "NATIVE"</td></tr>
 * </table>
 *
 * @see com.devops.dashboard.mcp.handler.EnvironmentHandler#envCreate(EnvCreateRequest)
 */
@Getter
@Builder
public class EnvCreateRequest {

    /** 环境名称（用户指定），为空时按 "type-timestamp" 格式自动生成 */
    private final String name;

    /** 目标主机标识符，必须对应 hosts.yml 中的有效主机 ID */
    private final String hostId;

    /** 环境类型：DEV / TEST / STAGING / PROD / EXPERIMENT */
    private final String environmentType;

    /** 隔离类型：DOCKER（默认） / NATIVE */
    private final String isolationType;

    /** 运行时版本约束（可选），如 "docker:26.0" */
    private final String runtimeConstraint;
}
