package com.devops.dashboard.application.service;

import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.experiment.*;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 实验管理核心服务
 * 负责Spike实验的全生命周期管理
 */
public interface ExperimentService {

    /**
     * 创建新的Spike实验
     * 会自动创建专用的实验环境
     *
     * @param request 实验请求（包含假设、所需服务等）
     * @return 创建的实验实例
     */
    Mono<Experiment> createSpike(SpikeRequest request);

    /**
     * 启动实验（开始运行）
     */
    Mono<Experiment> start(ExperimentId expId);

    /**
     * 提交实验结论
     * 包含证据数据和决策
     */
    Mono<Experiment> conclude(ExperimentId expId, Conclusion conclusion);

    /**
     * 记录实验证据数据
     */
    Mono<Experiment> recordEvidence(ExperimentId expId, Evidence evidence);

    /**
     * 归档实验
     * 生成Markdown报告到docs/spikes/
     * 销毁实验环境
     */
    Mono<Experiment> archive(ExperimentId expId);

    /**
     * 取消正在运行的实验
     */
    Mono<Experiment> cancel(ExperimentId expId);

    /**
     * 获取实验证据数据
     */
    Mono<Evidence> getEvidence(ExperimentId expId);

    /**
     * 列出指定状态的实验
     */
    Flux<Experiment> findByStatus(ExperimentStatus status);

    /**
     * 列出指定时间范围内的实验
     */
    Flux<Experiment> findByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * 根据ID查找实验
     */
    Mono<Experiment> findById(ExperimentId expId);
}
