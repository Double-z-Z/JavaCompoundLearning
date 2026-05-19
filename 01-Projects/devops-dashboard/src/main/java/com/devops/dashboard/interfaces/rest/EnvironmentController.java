package com.devops.dashboard.interfaces.rest;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.exception.environment.EnvironmentNotFoundException;
import com.devops.dashboard.interfaces.dto.CreateEnvironmentRequest;
import com.devops.dashboard.interfaces.dto.EnvironmentResponse;
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

import java.util.List;

@RestController
@RequestMapping("/environments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "环境管理", description = "环境的创建、查询、销毁等操作")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @PostMapping
    @Operation(summary = "创建环境", description = "根据规格说明创建新的DevOps环境")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "环境创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数无效"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<ResponseEntity<EnvironmentResponse>> createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request) {
        log.info("Creating environment: {}", request.getName());

        List<TargetNodeRef> targetNodeRefs = request.getTargetNodes() != null ?
                request.getTargetNodes().stream()
                        .map(dto -> TargetNodeRef.builder()
                                .nodeId(dto.getNodeId())
                                .ip(dto.getIp())
                                .role(dto.getRole())
                                .build())
                        .toList() :
                new java.util.ArrayList<>();

        EnvironmentSpec spec = EnvironmentSpec.builder()
                .type(request.getType())
                .resourceQuota(request.getResourceQuota() != null ? request.getResourceQuota() : ResourceQuota.development())
                .lifecyclePolicy(request.getLifecyclePolicy() != null ? request.getLifecyclePolicy() : LifecyclePolicy.defaultForDev())
                .targetNodes(targetNodeRefs)
                .build();

        return environmentService.createFromSpec(request.getName(), spec)
                .map(env -> {
                    log.info("Environment created successfully: {}", env.getId().getValue());
                    return ResponseEntity
                            .status(HttpStatus.CREATED)
                            .body(EnvironmentResponse.fromEntity(env));
                })
                .onErrorResume(e -> {
                    log.error("Failed to create environment: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @GetMapping
    @Operation(summary = "获取环境列表", description = "查询所有环境，支持按状态筛选")
    public Flux<EnvironmentResponse> listEnvironments(
            @Parameter(description = "环境状态筛选")
            @RequestParam(required = false) EnvironmentStatus status) {
        log.info("Listing environments with status filter: {}", status);

        if (status != null) {
            return environmentService.findByStatus(status)
                    .map(EnvironmentResponse::fromEntity);
        }

        return environmentService.findByStatus(null)
                .map(EnvironmentResponse::fromEntity);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取环境详情", description = "根据ID查询单个环境的详细信息")
    public Mono<ResponseEntity<EnvironmentResponse>> getEnvironment(
            @PathVariable String id) {
        log.info("Getting environment: {}", id);

        return environmentService.findById(EnvironmentId.of(id))
                .map(env -> ResponseEntity.ok(EnvironmentResponse.fromEntity(env)))
                .switchIfEmpty(Mono.error(new EnvironmentNotFoundException(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "销毁环境", description = "销毁指定环境及其所有服务实例")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> destroyEnvironment(@PathVariable String id) {
        log.info("Destroying environment: {}", id);

        return environmentService.destroy(EnvironmentId.of(id))
                .doOnSuccess(v -> log.info("Environment destroyed successfully: {}", id))
                .doOnError(e -> log.error("Failed to destroy environment {}: {}", id, e.getMessage()));
    }
}
