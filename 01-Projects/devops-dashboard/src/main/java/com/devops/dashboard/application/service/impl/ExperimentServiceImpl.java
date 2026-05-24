package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.application.service.ExperimentService;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.environment.EnvironmentSpec;
import com.devops.dashboard.domain.environment.EnvironmentType;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.experiment.Experiment;
import com.devops.dashboard.domain.experiment.ExperimentId;
import com.devops.dashboard.domain.experiment.ExperimentStatus;
import com.devops.dashboard.domain.experiment.SpikeRequest;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import com.devops.dashboard.infrastructure.experiment.ExperimentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * ExperimentService 实现
 * 遵循 Decision 2: Experiment 是独立聚合根，通过 EnvironmentId 引用环境
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentServiceImpl implements ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final EnvironmentService environmentService;

    private boolean archiveReportsEnabled = true;

    public void setArchiveReportsEnabled(boolean enabled) {
        this.archiveReportsEnabled = enabled;
    }

    @Override
    @Transactional
    public Mono<Experiment> createSpike(SpikeRequest request) {
        log.info("Creating spike experiment: {}", request.getTitle());

        Hypothesis hypothesis = request.getHypothesis();
        if (hypothesis == null) {
            hypothesis = Hypothesis.builder()
                    .statement(request.getTitle())
                    .build();
        }

        Experiment experiment = Experiment.createSpike(request);
        return saveExperiment(experiment);
    }

    @Override
    @Transactional
    public Mono<Experiment> start(ExperimentId expId) {
        log.info("Starting experiment: {}", expId.getValue());

        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }

        Experiment experiment = optExp.get();
        EnvironmentSpec spec = EnvironmentSpec.builder()
                .environmentType(EnvironmentType.EXPERIMENT)
                .resourceQuota(ResourceQuota.development())
                .lifecyclePolicy(LifecyclePolicy.forExperiment())
                .targetNodes(List.of())
                .build();

        String envName = "exp-env-" + experiment.getId().getValue();

        return environmentService.createFromSpec(envName, spec)
                .flatMap(env -> {
                    experiment.start(env.getId().getValue());
                    return saveExperiment(experiment);
                })
                .doOnSuccess(e -> log.info("Experiment started: {}", expId.getValue()));
    }

    @Override
    @Transactional
    public Mono<Experiment> conclude(ExperimentId expId, Conclusion conclusion) {
        log.info("Concluding experiment: {}", expId.getValue());

        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }

        Experiment experiment = optExp.get();
        experiment.conclude(conclusion);
        return saveExperiment(experiment)
                .doOnSuccess(e -> log.info("Experiment concluded: {}", expId.getValue()));
    }

    @Override
    @Transactional
    public Mono<Experiment> recordEvidence(ExperimentId expId, Evidence evidence) {
        log.info("Recording evidence for experiment: {}", expId.getValue());

        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }

        Experiment experiment = optExp.get();
        experiment.recordEvidence(evidence);
        return saveExperiment(experiment);
    }

    @Override
    @Transactional
    public Mono<Experiment> archive(ExperimentId expId) {
        log.info("Archiving experiment: {}", expId.getValue());

        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }

        Experiment experiment = optExp.get();
        String archivePath = generateArchiveReport(experiment);
        experiment.archive(archivePath);
        Experiment saved = experimentRepository.save(experiment);

        String envId = experiment.getEnvironmentId();
        if (envId != null) {
            return environmentService.destroy(EnvironmentId.of(envId))
                    .thenReturn(saved);
        }
        return Mono.just(saved);
    }

    @Override
    @Transactional
    public Mono<Experiment> cancel(ExperimentId expId) {
        log.info("Cancelling experiment: {}", expId.getValue());

        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }

        Experiment experiment = optExp.get();
        experiment.cancel();
        Experiment saved = experimentRepository.save(experiment);

        String envId = experiment.getEnvironmentId();
        if (envId != null) {
            return environmentService.destroy(EnvironmentId.of(envId))
                    .thenReturn(saved);
        }
        return Mono.just(saved);
    }

    @Override
    public Mono<Evidence> getEvidence(ExperimentId expId) {
        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }
        return Mono.just(optExp.get().getEvidence());
    }

    @Override
    public Flux<Experiment> findByStatus(ExperimentStatus status) {
        if (status == null) {
            return Flux.fromIterable(experimentRepository.findAll());
        }
        return Flux.fromIterable(experimentRepository.findByStatus(status));
    }

    @Override
    public Flux<Experiment> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return Flux.fromIterable(experimentRepository.findAll())
                .filter(e -> {
                    LocalDateTime created = e.getCreatedAt();
                    return created != null && !created.isBefore(start) && !created.isAfter(end);
                });
    }

    @Override
    public Mono<Experiment> findById(ExperimentId expId) {
        Optional<Experiment> optExp = experimentRepository.findById(expId);
        if (optExp.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Experiment not found: " + expId.getValue()));
        }
        return Mono.just(optExp.get());
    }

    private Mono<Experiment> saveExperiment(Experiment experiment) {
        return Mono.fromCallable(() -> experimentRepository.save(experiment));
    }

    private String generateArchiveReport(Experiment experiment) {
        if (!archiveReportsEnabled) {
            log.info("Archive report generation disabled, skipping file I/O");
            return "archive report skipped (disabled)";
        }

        String experimentId = experiment.getId().getValue();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = experimentId + "-" + timestamp + ".md";

        Path docsDir = Paths.get("docs/spikes");
        Path filePath = docsDir.resolve(filename);

        try {
            Files.createDirectories(docsDir);
            StringBuilder report = new StringBuilder();
            report.append("# Spike实验归档报告\n\n");
            report.append("## 基本信息\n\n");
            report.append("- **实验ID**: ").append(experimentId).append("\n");
            report.append("- **标题**: ").append(experiment.getTitle()).append("\n");
            report.append("- **创建者**: ").append(experiment.getCreatedBy()).append("\n");
            report.append("- **创建时间**: ").append(experiment.getCreatedAt()).append("\n");
            report.append("- **归档时间**: ").append(LocalDateTime.now()).append("\n\n");

            if (experiment.getHypothesis() != null) {
                report.append("## 假设\n\n");
                report.append("- **陈述**: ").append(experiment.getHypothesis().getStatement()).append("\n");
                if (experiment.getHypothesis().getBackground() != null) {
                    report.append("- **背景**: ").append(experiment.getHypothesis().getBackground()).append("\n");
                }
                report.append("\n");
            }

            if (experiment.getEvidence() != null) {
                report.append("## 证据数据\n\n");
                report.append("- **收集时间**: ").append(experiment.getEvidence().getCollectedAt()).append("\n");
                report.append("- **指标**: ").append(experiment.getEvidence().getMetrics()).append("\n");
                report.append("- **附件**: ").append(experiment.getEvidence().getArtifacts()).append("\n\n");
            }

            if (experiment.getConclusion() != null) {
                report.append("## 结论\n\n");
                report.append("- **决策**: ").append(experiment.getConclusion().getDecision()).append("\n");
                report.append("- **摘要**: ").append(experiment.getConclusion().getSummary()).append("\n");
                report.append("- **经验教训**: ").append(experiment.getConclusion().getLessonsLearned()).append("\n");
                report.append("- **后续步骤**: ").append(experiment.getConclusion().getNextSteps()).append("\n\n");
            }

            Files.writeString(filePath, report.toString());
            log.info("Archive report generated: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("Failed to generate archive report: {}", e.getMessage());
            return "docs/spikes/" + filename + " (generation failed)";
        }
    }
}