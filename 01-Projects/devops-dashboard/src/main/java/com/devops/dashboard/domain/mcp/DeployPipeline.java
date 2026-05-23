package com.devops.dashboard.domain.mcp;

import com.devops.dashboard.domain.environment.EnvironmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 部署流水线聚合根（Aggregate Root）。
 *
 * <p>V3 新增的编排聚合根，作为 MCP 部署领域的【首选入口】。
 * 内部封装完整部署流水线的原子化编排，采用 Saga 模式实现失败补偿。</p>
 *
 * <h3>执行流程</h3>
 * <pre>{@code
 * 1. stageCreateEnv()     → CREATING
 * 2. stageDeployService() → DEPLOYING
 * 3. stageHealthCheck()   → VERIFYING
 * 4. stageNetworkVerify() → ANALYZING
 * 5. SUCCEEDED
 *
 * 任何阶段失败 → 触发 compensate() → COMPENSATED
 * }</pre>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>入口唯一性：deploy_pipeline 是部署的【首选入口】，AI 不应手动分步调用子工具</li>
 *   <li>原子性：任何子步骤失败 → 整体失败 → 触发补偿清理</li>
 *   <li>幂等性：补偿操作必须是幂等的（env_destroy 对已销毁环境无操作）</li>
 * </ul>
 */
public class DeployPipeline {

    private static final Logger log = LoggerFactory.getLogger(DeployPipeline.class);

    private final PipelineId id;
    private final DeploySpec spec;
    private PipelineStatus status;
    private final List<PipelineStage> stages;
    private EnvironmentId createdEnvId;
    private final Instant createdAt;
    private Instant completedAt;

    public DeployPipeline(PipelineId id, DeploySpec spec) {
        this.id = id;
        this.spec = spec;
        this.status = PipelineStatus.PENDING;
        this.stages = new ArrayList<>();
        this.createdAt = Instant.now();
        initStages();
    }

    private void initStages() {
        stages.add(PipelineStage.of("env_create"));
        stages.add(PipelineStage.of("env_deploy"));
        stages.add(PipelineStage.of("health_check"));
        stages.add(PipelineStage.of("network_verify"));
    }

    public PipelineId getId() { return id; }
    public DeploySpec getSpec() { return spec; }
    public PipelineStatus getStatus() { return status; }
    public List<PipelineStage> getStages() { return List.copyOf(stages); }
    public EnvironmentId getCreatedEnvId() { return createdEnvId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    /**
     * 执行完整流水线（Saga）。
     *
     * @param executor 执行器接口
     * @return 流水线执行结果
     */
    public Mono<PipelineResult> execute(PipelineExecutor executor) {
        log.info("Pipeline {} starting execution", id.value());
        status = PipelineStatus.CREATING;

        return stageCreateEnv(executor)
            .flatMap(envId -> {
                this.createdEnvId = envId;
                return stageDeployService(executor, envId);
            })
            .flatMap(envId -> stageHealthCheck(executor, envId).thenReturn(envId))
            .flatMap(envId -> stageNetworkVerify(executor, envId).thenReturn(envId))
            .map(envId -> buildSuccessResult(envId))
            .onErrorResume(error -> compensate(executor, error));
    }

    private Mono<EnvironmentId> stageCreateEnv(PipelineExecutor executor) {
        updateStageStatus(0, StageStatus.RUNNING, null);
        return executor.createEnv(spec)
            .doOnSuccess(envId -> {
                updateStageStatus(0, StageStatus.SUCCEEDED, "环境已创建: " + envId.value());
                status = PipelineStatus.DEPLOYING;
            })
            .doOnError(error -> {
                updateStageStatus(0, StageStatus.FAILED, error.getMessage());
            });
    }

    private Mono<EnvironmentId> stageDeployService(PipelineExecutor executor, EnvironmentId envId) {
        updateStageStatus(1, StageStatus.RUNNING, null);
        return executor.deployService(envId, spec)
            .doOnSuccess(id -> {
                updateStageStatus(1, StageStatus.SUCCEEDED, "服务已部署");
                status = PipelineStatus.VERIFYING;
            })
            .doOnError(error -> {
                updateStageStatus(1, StageStatus.FAILED, error.getMessage());
            });
    }

    private Mono<String> stageHealthCheck(PipelineExecutor executor, EnvironmentId envId) {
        updateStageStatus(2, StageStatus.RUNNING, null);
        return executor.healthCheck(envId, spec)
            .doOnSuccess(result -> {
                updateStageStatus(2, StageStatus.SUCCEEDED, result);
                status = PipelineStatus.ANALYZING;
            })
            .doOnError(error -> {
                updateStageStatus(2, StageStatus.FAILED, error.getMessage());
            });
    }

    private Mono<String> stageNetworkVerify(PipelineExecutor executor, EnvironmentId envId) {
        updateStageStatus(3, StageStatus.RUNNING, null);
        return executor.networkVerify(envId, spec)
            .doOnSuccess(result -> {
                updateStageStatus(3, StageStatus.SUCCEEDED, result);
                status = PipelineStatus.SUCCEEDED;
                this.completedAt = Instant.now();
            })
            .doOnError(error -> {
                updateStageStatus(3, StageStatus.FAILED, error.getMessage());
                // 网络分析失败不触发补偿，仅报告警告
                status = PipelineStatus.SUCCEEDED;
                this.completedAt = Instant.now();
            });
    }

    /**
     * 补偿清理：失败时销毁已创建的环境。
     */
    private Mono<PipelineResult> compensate(PipelineExecutor executor, Throwable error) {
        log.warn("Pipeline {} failed, initiating compensation", id.value(), error);

        if (createdEnvId != null && !spec.keepOnFailure()) {
            return executor.destroyEnv(createdEnvId)
                .doOnSuccess(v -> log.info("Pipeline {} compensation completed", id.value()))
                .doOnError(e -> log.error("Pipeline {} compensation failed", id.value(), e))
                .thenReturn(buildFailureResult(error))
                .onErrorReturn(buildFailureResult(error));
        }

        status = PipelineStatus.COMPENSATED;
        return Mono.just(buildFailureResult(error));
    }

    private void updateStageStatus(int index, StageStatus stageStatus, String output) {
        if (index < stages.size()) {
            PipelineStage stage = stages.get(index);
            stages.set(index, switch (stageStatus) {
                case RUNNING -> stage.start();
                case SUCCEEDED -> stage.complete(output != null ? output : "success");
                case FAILED -> stage.fail(output != null ? output : "failed");
                default -> stage.withStatus(stageStatus);
            });
        }
    }

    private PipelineResult buildSuccessResult(EnvironmentId envId) {
        return new PipelineResult(
            id.value(),
            PipelineStatus.SUCCEEDED.name(),
            envId.value(),
            stages,
            createdAt,
            Instant.now(),
            null
        );
    }

    private PipelineResult buildFailureResult(Throwable error) {
        status = PipelineStatus.COMPENSATED;
        return new PipelineResult(
            id.value(),
            PipelineStatus.COMPENSATED.name(),
            null,
            stages,
            createdAt,
            Instant.now(),
            new PipelineError(error.getMessage(), List.of("env_list", "env_get_logs"))
        );
    }

    /**
     * Pipeline 执行器接口。
     * 实际实现由 PipelineHandler 注入，负责调用底层 MCP Tools。
     */
    public interface PipelineExecutor {
        Mono<EnvironmentId> createEnv(DeploySpec spec);
        Mono<EnvironmentId> deployService(EnvironmentId envId, DeploySpec spec);
        Mono<String> healthCheck(EnvironmentId envId, DeploySpec spec);
        Mono<String> networkVerify(EnvironmentId envId, DeploySpec spec);
        Mono<Void> destroyEnv(EnvironmentId envId);
    }

    /**
     * 流水线执行结果（Record）。
     */
    public record PipelineResult(
        String pipelineId,
        String status,
        String envId,
        List<PipelineStage> stages,
        Instant createdAt,
        Instant completedAt,
        PipelineError error
    ) {}

    /**
     * 流水线错误（Record）。
     */
    public record PipelineError(
        String message,
        List<String> nextSteps
    ) {}
}