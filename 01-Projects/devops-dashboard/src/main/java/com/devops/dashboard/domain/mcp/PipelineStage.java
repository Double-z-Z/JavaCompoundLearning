package com.devops.dashboard.domain.mcp;

import java.time.Instant;

/**
 * 流水线阶段（Value Object）。
 *
 * @param name       阶段名称：env_create | env_deploy | health_check | network_verify
 * @param status     阶段状态
 * @param output     阶段输出摘要
 * @param startedAt  开始时间
 * @param completedAt 完成时间
 */
public record PipelineStage(
    String name,
    StageStatus status,
    String output,
    Instant startedAt,
    Instant completedAt
) {
    public static PipelineStage of(String name) {
        return new PipelineStage(name, StageStatus.PENDING, null, null, null);
    }

    public PipelineStage withStatus(StageStatus status) {
        return new PipelineStage(name, status, output, startedAt, completedAt);
    }

    public PipelineStage withOutput(String output) {
        return new PipelineStage(name, status, output, startedAt, completedAt);
    }

    public PipelineStage start() {
        return new PipelineStage(name, StageStatus.RUNNING, output, Instant.now(), null);
    }

    public PipelineStage complete(String output) {
        return new PipelineStage(name, StageStatus.SUCCEEDED, output, startedAt, Instant.now());
    }

    public PipelineStage fail(String error) {
        return new PipelineStage(name, StageStatus.FAILED, error, startedAt, Instant.now());
    }
}