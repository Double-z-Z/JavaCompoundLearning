package com.devops.dashboard.domain.mcp;

import java.util.UUID;

/**
 * 部署流水线聚合根标识（Value Object）。
 *
 * @param value 流水线 ID 字符串
 */
public record PipelineId(String value) {

    public static PipelineId generate() {
        return new PipelineId("pipe-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static PipelineId of(String value) {
        return new PipelineId(value);
    }
}