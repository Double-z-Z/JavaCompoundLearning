package com.devops.dashboard.interfaces.rest;

import com.devops.dashboard.application.service.ExperimentService;
import com.devops.dashboard.domain.experiment.Experiment;
import com.devops.dashboard.domain.experiment.ExperimentId;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import com.devops.dashboard.interfaces.dto.ConclusionRequest;
import com.devops.dashboard.interfaces.dto.CreateExperimentRequest;
import com.devops.dashboard.interfaces.dto.ExperimentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Spike实验管理", description = "Spike实验的创建、启动、结论、归档全生命周期")
public class ExperimentController {

    private final ExperimentService experimentService;

    @PostMapping
    @Operation(summary = "创建Spike实验", description = "创建新的Spike实验，处于PLANNING状态")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "实验创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    public Mono<ResponseEntity<ExperimentResponse>> createExperiment(
            @Valid @RequestBody CreateExperimentRequest request) {
        log.info("Creating experiment: {}", request.getTitle());

        Hypothesis hypothesis = Hypothesis.builder()
                .statement(request.getHypothesisStatement())
                .background(request.getHypothesisBackground())
                .successCriteria(Collections.emptyList()) // 前端传字符串，暂用空列表
                .build();

        com.devops.dashboard.domain.experiment.SpikeRequest spikeRequest =
                com.devops.dashboard.domain.experiment.SpikeRequest.builder()
                        .title(request.getTitle())
                        .createdBy(request.getCreatedBy())
                        .hypothesis(hypothesis)
                        .build();

        return experimentService.createSpike(spikeRequest)
                .map(e -> ResponseEntity.status(HttpStatus.CREATED).body(ExperimentResponse.fromEntity(e)));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "启动实验", description = "将实验状态改为RUNNING，创建专用实验环境")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "实验启动成功"),
            @ApiResponse(responseCode = "404", description = "实验不存在"),
            @ApiResponse(responseCode = "400", description = "状态转移无效")
    })
    public Mono<ResponseEntity<ExperimentResponse>> startExperiment(
            @Parameter(description = "实验ID") @PathVariable String id) {
        log.info("Starting experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);
        return experimentService.start(expId)
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)));
    }

    @GetMapping
    @Operation(summary = "获取实验列表", description = "查询所有实验，支持按状态筛选")
    public Flux<ExperimentResponse> listExperiments(
            @Parameter(description = "实验状态筛选") @RequestParam(required = false) String status) {
        log.info("Listing experiments with status filter: {}", status);

        if (status != null && !status.isBlank()) {
            return experimentService.findByStatus(com.devops.dashboard.domain.experiment.ExperimentStatus.valueOf(status))
                    .map(ExperimentResponse::fromEntity);
        }

        return experimentService.findByStatus(null)
                .map(ExperimentResponse::fromEntity);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取实验详情", description = "根据ID查询单个实验的详细信息")
    public Mono<ResponseEntity<ExperimentResponse>> getExperiment(
            @Parameter(description = "实验ID") @PathVariable String id) {
        log.info("Getting experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);
        return experimentService.findById(expId)
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)))
                .onErrorResume(e -> Mono.error(new IllegalArgumentException("Experiment not found: " + id)));
    }

    @PostMapping("/{id}/evidence")
    @Operation(summary = "记录实验证据", description = "为正在运行的实验添加证据数据")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "证据记录成功"),
            @ApiResponse(responseCode = "400", description = "实验未在运行状态")
    })
    public Mono<ResponseEntity<ExperimentResponse>> recordEvidence(
            @Parameter(description = "实验ID") @PathVariable String id,
            @RequestBody java.util.Map<String, String> evidenceData) {
        log.info("Recording evidence for experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);

        Evidence evidence = Evidence.builder()
                .collectedAt(LocalDateTime.now())
                .metrics(Collections.emptyList()) // 前端传字符串，暂用空列表
                .artifacts(Collections.emptyList())
                .build();

        return experimentService.recordEvidence(expId, evidence)
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)));
    }

    @PostMapping("/{id}/conclude")
    @Operation(summary = "提交结论", description = "为实验添加结论并标记为COMPLETED")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "结论提交成功"),
            @ApiResponse(responseCode = "400", description = "状态转移无效")
    })
    public Mono<ResponseEntity<ExperimentResponse>> concludeExperiment(
            @Parameter(description = "实验ID") @PathVariable String id,
            @Valid @RequestBody ConclusionRequest request) {
        log.info("Concluding experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);
        return experimentService.conclude(expId, request.toConclusion())
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档实验", description = "归档实验并生成Markdown报告，销毁实验环境")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "归档成功"),
            @ApiResponse(responseCode = "400", description = "实验未完成无法归档")
    })
    public Mono<ResponseEntity<ExperimentResponse>> archiveExperiment(
            @Parameter(description = "实验ID") @PathVariable String id) {
        log.info("Archiving experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);
        return experimentService.archive(expId)
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消实验", description = "取消正在运行的实验，销毁关联环境")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "取消成功"),
            @ApiResponse(responseCode = "400", description = "状态转移无效")
    })
    public Mono<ResponseEntity<ExperimentResponse>> cancelExperiment(
            @Parameter(description = "实验ID") @PathVariable String id) {
        log.info("Cancelling experiment: {}", id);

        ExperimentId expId = ExperimentId.of(id);
        return experimentService.cancel(expId)
                .map(e -> ResponseEntity.ok(ExperimentResponse.fromEntity(e)));
    }
}