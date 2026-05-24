package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.application.service.ServiceManifest;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.environment.EnvironmentCreationException;
import com.devops.dashboard.domain.exception.environment.EnvironmentNotFoundException;
import com.devops.dashboard.domain.host.Capability;
import com.devops.dashboard.domain.host.HostId;
import com.devops.dashboard.domain.host.HostRole;
import com.devops.dashboard.infrastructure.environment.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

/**
 * 环境管理服务实现（全响应式版本）。
 *
 * <p>所有操作均通过 Reactor 响应式链编排，阻塞型操作（Docker 命令、Provisioner 调用）
 * 通过 {@link Schedulers#boundedElastic()} 调度到独立线程池，避免阻塞 Netty I/O 线程。</p>
 *
 * <h3>线程模型</h3>
 * <pre>
 *   Netty I/O Thread (WebFlux)
 *     → validateTargetHost()          [同步，快速]
 *     → provisioner.provision()        [调度到 elastic 线程]
 *     → repository.save()              [事务边界内，同步]
 *
 *   阻塞操作专用: Schedulers.boundedElastic()
 *     - Docker Compose up/down (30s+)
 *     - docker run/port/stop/start
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentServiceImpl.class);

    private final EnvironmentRepository environmentRepository;
    private final HostService hostService;
    private final EnvironmentProvisioner provisioner;

    @Override
    public Mono<Environment> createFromSpec(String name, EnvironmentSpec spec) {
        return Mono.fromRunnable(() -> validateTargetHost(spec))
                .then(Mono.defer(() -> {
                    String envName = (name != null && !name.isBlank())
                            ? name : generateEnvironmentName(spec.getEnvironmentType());
                    log.info("[createFromSpec] name={}, environmentType={}, hostId={}", envName, spec.getEnvironmentType(), spec.getHostId());
                    return provisioner.provision(spec)
                            .subscribeOn(Schedulers.boundedElastic())
                            .switchIfEmpty(Mono.error(new EnvironmentCreationException(
                                    "Provisioner returned empty result for " + envName)))
                            .flatMap(provisioned -> {
                                if (provisioned == null) {
                                    return Mono.error(new EnvironmentCreationException(
                                            "Provisioner returned null for " + envName));
                                }
                                // V3: 使用 markAsReadyFromExternal() 直接设置状态，跳过状态转换验证
                                // 因为 provisioned 代表的是"外部系统已就绪的环境"，不是"新创建的"
                                if (provisioned.getStatus() == EnvironmentStatus.CREATING) {
                                    provisioned.markAsReadyFromExternal(Map.of(
                                            "provisionedAt", java.time.LocalDateTime.now().toString(),
                                            "provisionedBy", "docker-compose"
                                    ));
                                } else {
                                    provisioned.getAccessEndpoints().putAll(Map.of(
                                            "provisionedAt", java.time.LocalDateTime.now().toString(),
                                            "provisionedBy", "docker-compose"
                                    ));
                                }
                                return Mono.just(environmentRepository.save(provisioned));
                            })
                            .doOnNext(saved -> log.info(
                                    "[createFromSpec] Environment created: id={}, status={}",
                                    saved.getIdValue(), saved.getStatus()))
                            .onErrorResume(e -> {
                                log.error("[createFromSpec] Provisioning failed for {}: {}", envName, e.getMessage());
                                return Mono.error(new EnvironmentCreationException(
                                        "Failed to provision '" + envName + "': " + e.getMessage(), e));
                            });
                }));
    }

    @Override
    public Mono<Void> destroy(EnvironmentId envId) {
        return Mono.fromCallable(() -> environmentRepository.findById(envId)
                .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue())))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(environment ->
                    // V3: 即使 teardown 失败也要标记为 DESTROYED，避免状态不一致
                    provisioner.teardown(envId)
                        .doOnSubscribe(s -> log.info("[destroy] Destroying environment: {}", envId.getValue()))
                        .doOnSuccess(v -> log.info("[destroy] Teardown completed for {}", envId.getValue()))
                        .doOnError(e -> log.error("[destroy] Teardown failed for {}: {}", envId.getValue(), e.getMessage()))
                        .onErrorResume(e -> {
                            // V3: 吞掉 teardown 错误，继续标记为 DESTROYED
                            log.warn("[destroy] Teardown failed, proceeding with status update: {}", envId.getValue());
                            return Mono.empty();
                        })
                        .then(Mono.fromRunnable(() -> {
                            environment.markAsDestroyed();
                            environmentRepository.save(environment);
                            log.info("[destroy] Environment marked as destroyed: {}", envId.getValue());
                        }))
                );
    }

    @Override
    public Mono<ServiceInstance> deployService(EnvironmentId envId, ServiceManifest manifest) {
        return Mono.fromCallable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            String image = manifest.getImage() != null ? manifest.getImage() : resolveDefaultImage(manifest.getServiceName());
            ServiceInstance instance = ServiceInstance.create(manifest.getServiceName(), image);
            instance.markAsDeploying();
            environment.addService(instance);
            environmentRepository.save(environment);
            return Map.of("environment", environment, "instance", instance);
        }).flatMap(ctx -> {
            @SuppressWarnings("unchecked")
            Environment environment = (Environment) ctx.get("environment");
            @SuppressWarnings("unchecked")
            ServiceInstance instance = (ServiceInstance) ctx.get("instance");

            return provisioner.deployService(envId, instance.getServiceTemplate(), instance.getImage(), instance.getInstanceId())
                    .map(endpoints -> {
                        if (endpoints != null && !endpoints.isEmpty()) {
                            environment.getAccessEndpoints().putAll(endpoints);
                        }
                        instance.markAsRunning(instance.getInstanceId());
                        environmentRepository.save(environment);
                        return instance;
                    })
                    .onErrorResume(e -> {
                        log.error("[deployService] Deployment failed: {}", e.getMessage(), e);
                        instance.markAsFailed(e.getMessage());
                        environmentRepository.save(environment);
                        return Mono.just(instance);
                    });
        });
    }

    @Override
    public Mono<Void> stopService(EnvironmentId envId, String instanceId) {
        return Mono.fromCallable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            return environment.findServiceByInstanceId(instanceId)
                    .map(si -> Map.of("env", environment, "si", si))
                    .orElse(null);
        }).flatMap(ctx -> {
            if (ctx == null) return Mono.empty();
            @SuppressWarnings("unchecked")
            Environment environment = (Environment) ctx.get("env");
            @SuppressWarnings("unchecked")
            ServiceInstance si = (ServiceInstance) ctx.get("si");

            return provisioner.stopService(envId, si.getInstanceId())
                    .doOnSuccess(v -> {
                        si.markAsStopped();
                        environmentRepository.save(environment);
                    })
                    .onErrorResume(e -> {
                        log.warn("[stopService] Error: {}", e.getMessage());
                        si.markAsStopped();
                        environmentRepository.save(environment);
                        return Mono.empty();
                    });
        }).then();
    }

    @Override
    public Mono<ServiceInstance> restartService(EnvironmentId envId, String instanceId) {
        return Mono.fromCallable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            ServiceInstance instance = environment.findServiceByInstanceId(instanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found: " + instanceId));
            return Map.of("env", environment, "si", instance);
        }).flatMap(ctx -> {
            @SuppressWarnings("unchecked")
            Environment environment = (Environment) ctx.get("env");
            @SuppressWarnings("unchecked")
            ServiceInstance instance = (ServiceInstance) ctx.get("si");

            instance.markAsStopped();
            instance.markAsDeploying();

            return provisioner.stopService(envId, instance.getInstanceId())
                    .then(provisioner.startService(envId, instance.getInstanceId(), instance.getImage()))
                    .map(v -> {
                        instance.markAsRunning(instance.getInstanceId());
                        environmentRepository.save(environment);
                        return instance;
                    })
                    .onErrorResume(e -> {
                        log.error("[restartService] Failed: {}", e.getMessage(), e);
                        instance.markAsFailed(e.getMessage());
                        environmentRepository.save(environment);
                        return Mono.just(instance);
                    });
        });
    }

    @Override
    public Mono<EnvironmentStatus> getStatus(EnvironmentId envId) {
        return provisioner.checkStatus(envId)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("[getStatus] checkStatus failed, fallback to DB: {}", e.getMessage());
                    return findById(envId).map(Environment::getStatus);
                });
    }

    @Override
    public Flux<ServiceInstance> listServices(EnvironmentId envId) {
        return Flux.fromIterable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            return environment.getServices().iterator();
        });
    }

    @Override
    public Mono<Environment> findById(EnvironmentId envId) {
        return Mono.fromCallable(() ->
                environmentRepository.findById(envId)
                        .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()))
        );
    }

    @Override
    public Flux<Environment> findByStatus(EnvironmentStatus status) {
        if (status != null) {
            return Flux.fromIterable(environmentRepository.findByStatus(status));
        }
        return Flux.fromIterable(environmentRepository.findAll());
    }

    @Override
    public Mono<Map<String, String>> getAccessEndpoints(EnvironmentId envId) {
        return Mono.fromCallable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            return environment.getAccessEndpoints();
        });
    }

    @Override
    public Flux<Environment> listAll() {
        return Flux.fromIterable(environmentRepository.findAll())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validateTargetHost(EnvironmentSpec spec) {
        String hostIdValue = spec.getHostId();
        if (hostIdValue == null || hostIdValue.isBlank()) {
            log.debug("No target host specified, skipping host validation");
            return;
        }
        HostId hostId = HostId.of(hostIdValue);
        hostService.validateRole(hostId, HostRole.TARGET);
        IsolationType isolation = spec.getIsolationType();
        if (isolation == IsolationType.DOCKER) {
            hostService.validateCapability(hostId, Capability.DOCKER);
        }
    }

    private String generateEnvironmentName(EnvironmentType type) {
        String prefix = switch (type) {
            case DEV -> "dev";
            case TEST -> "test";
            case STAGING -> "staging";
            case PROD -> "prod";
            case EXPERIMENT -> "exp";
        };
        return prefix + "-" + System.currentTimeMillis();
    }

    private String resolveDefaultImage(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "nacos", "nacos-server" -> "nacos/nacos-server:v2.3.0";
            case "mysql" -> "mysql:8.0";
            case "redis" -> "redis:7-alpine";
            case "nginx" -> "nginx:alpine";
            default -> serviceName + ":latest";
        };
    }
}
