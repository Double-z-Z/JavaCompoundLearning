package com.devops.dashboard.domain.experiment;

import com.devops.dashboard.domain.environment.EnvironmentSpec;
import com.devops.dashboard.domain.environment.TargetNodeRef;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.List;

@Getter
@Builder
public class SpikeRequest {
    
    private final String title;
    private final String createdBy;
    private final Hypothesis hypothesis;
    private final List<String> serviceTemplates;  // 引用的服务模板名列表
    private final EnvironmentSpec environmentSpec;  // 实验环境规格（可选，使用默认值）
    private final Duration maxLifetime;  // 实验最大存活时间
    
    public static SpikeRequestBuilder builder() {
        return new SpikeRequestBuilder()
            .maxLifetime(Duration.ofHours(2));  // 默认2小时
    }
}
