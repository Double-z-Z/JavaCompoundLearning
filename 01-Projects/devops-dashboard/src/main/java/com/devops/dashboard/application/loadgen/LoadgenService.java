package com.devops.dashboard.application.loadgen;

import com.devops.dashboard.domain.loadgen.*;
import reactor.core.publisher.Mono;

/**
 * 负载生成（Loadgen）应用服务接口。
 *
 * <p>作为压测操作的编排入口，封装以下核心能力：</p>
 * <ul>
 *   <li><strong>压测执行</strong>：在指定压测机上运行 wrk/hey/ab 等工具</li>
 *   <li><strong>健康检查</strong>：验证目标服务的可用性</li>
 *   <li><strong>远程命令</strong>：在目标主机上执行任意命令（保守式交互）</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <p>所有写操作和远程命令执行均需满足：</p>
 * <ol>
 *   <li>压测机必须具备 {@code LOADGEN} 角色</li>
 *   <li>压测机必须安装指定的 {@code LoadgenTool}</li>
 *   <li>命令执行前需校验主机存在性和角色合法性</li>
 * </ol>
 *
 * @see LoadTestSpec 压测规格
 * @see LoadTestResult 压测结果
 */
public interface LoadgenService {

    /**
     * 执行负载测试。
     *
     * @param spec 压测规格（包含目标URL、并发数、持续时间等）
     * @return 压测结果，包含 QPS、延迟分布等指标
     */
    Mono<LoadTestResult> executeLoadTest(LoadTestSpec spec);

    /**
     * 执行健康检查。
     *
     * @param spec 健康检查规格（目标URL、超时、期望状态码）
     * @return 健康检查结果
     */
    Mono<HealthCheckResult> executeHealthCheck(HealthCheckSpec spec);

    /**
     * 在指定主机上执行远程命令。
     *
     * <p><strong>保守式交互</strong>：此方法仅用于只读诊断命令，
     * 写操作应通过专门的 Tool 并要求用户显式确认。</p>
     *
     * @param hostId 目标主机 ID
     * @param command 要执行的命令
     * @param timeoutSeconds 超时秒数
     * @return 命令执行结果（stdout/stderr/exitCode）
     */
    Mono<CommandExecutionResult> executeCommand(String hostId, String command, int timeoutSeconds);

    /**
     * 查询指定主机上可用的压测工具列表。
     *
     * @param loadgenHostId 压测机 ID
     * @return 可用工具列表
     */
    java.util.List<String> getAvailableTools(String loadgenHostId);
}
