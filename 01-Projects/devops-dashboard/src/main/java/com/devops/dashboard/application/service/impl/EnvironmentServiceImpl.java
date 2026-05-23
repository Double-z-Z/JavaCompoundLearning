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
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
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
    private static final String DOCKER_NOT_AVAILABLE = "Docker not available";

    private final EnvironmentRepository environmentRepository;
    private final HostService hostService;
    private final EnvironmentProvisioner provisioner;

    @Override
    public Mono<Environment> createFromSpec(String name, EnvironmentSpec spec) {
        return Mono.fromRunnable(() -> validateTargetHost(spec))
                .then(Mono.defer(() -> {
                    String envName = (name != null && !name.isBlank())
                            ? name : generateEnvironmentName(spec.getType());
                    log.info("[createFromSpec] name={}, type={}, hostId={}", envName, spec.getType(), spec.getHostId());
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
            ServiceInstance instance = ServiceInstance.create(
                    manifest.getTemplateName(),
                    manifest.getImage() != null ? manifest.getImage() : resolveDefaultImage(manifest.getTemplateName())
            );
            instance.markAsDeploying();
            environment.addService(instance);
            environmentRepository.save(environment);
            return Map.of("environment", environment, "instance", instance);
        }).flatMap(ctx -> {
            @SuppressWarnings("unchecked")
            Environment environment = (Environment) ctx.get("environment");
            @SuppressWarnings("unchecked")
            ServiceInstance instance = (ServiceInstance) ctx.get("instance");

            return executeDockerDeploy(environment, instance)
                    .map(endpoints -> {
                        if (endpoints != null && !endpoints.isEmpty()) {
                            environment.getAccessEndpoints().putAll(endpoints);
                        }
                        environmentRepository.save(environment);
                        return instance;
                    })
                    .onErrorResume(e -> {
                        log.error("[deployService] Container deployment failed: {}", e.getMessage(), e);
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

            return executeDockerStop(envId.getValue(), si)
                    .doOnSuccess(v -> {
                        si.markAsStopped();
                        environmentRepository.save(environment);
                    })
                    .onErrorResume(e -> {
                        log.warn("[stopService] Error stopping container: {}", e.getMessage());
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

            return executeDockerStop(envId.getValue(), instance)
                    .then(executeDockerStart(environment, instance))
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
        RuntimeType runtime = spec.getRuntime();
        if (runtime == RuntimeType.DOCKER) {
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

    private String resolveDefaultImage(String templateName) {
        return switch (templateName.toLowerCase()) {
            case "nacos", "nacos-server" -> "nacos/nacos-server:v2.3.0";
            case "mysql" -> "mysql:8.0";
            case "redis" -> "redis:7-alpine";
            case "nginx" -> "nginx:alpine";
            default -> templateName + ":latest";
        };
    }

    private Mono<Map<String, String>> executeDockerDeploy(Environment environment, ServiceInstance instance) {
        return checkDockerAvailable()
                .flatMap(available -> {
                    if (!available) {
                        return Mono.error(new RuntimeException(DOCKER_NOT_AVAILABLE));
                    }
                    return doDockerRun(environment, instance);
                });
    }

    private Mono<Boolean> checkDockerAvailable() {
        return Mono.fromCallable(() -> {
            try {
                Process p = new ProcessBuilder("docker", "info")
                        .redirectErrorStream(true).start();
                boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) p.destroyForcibly();
                return finished && p.exitValue() == 0;
            } catch (Exception e) {
                log.warn("[checkDockerAvailable] Docker not available: {}", e.getMessage());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Map<String, String>> doDockerRun(Environment environment, ServiceInstance instance) {
        return Mono.fromCallable(() -> {
            String containerName = "svc-" + environment.getIdValue() + "-" + instance.getInstanceId().substring(0, 8);
            String image = instance.getImage();
            log.info("[deployContainer] docker run -d --name {} {}", containerName, image);

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "-d",
                    "--name", containerName,
                    "--label", "devops.env=" + environment.getIdValue(),
                    "--label", "devops.service=" + instance.getServiceTemplate(),
                    image
            );
            pb.redirectErrorStream(true);

            try {
                Process process = pb.start();
                boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new RuntimeException("docker run timed out after 60s");
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    String error = new String(process.getErrorStream().readAllBytes()).trim();
                    throw new RuntimeException("docker run failed (exit=" + exitCode + "): " + error);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during docker run", e);
            }

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            return collectContainerEndpoints(containerName, image);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, String> collectContainerEndpoints(String containerName, String image) {
        java.util.LinkedHashMap<String, String> endpoints = new java.util.LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "port", containerName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            String portOutput = new String(process.getInputStream().readAllBytes()).trim();

            if (!portOutput.isBlank() && !portOutput.contains("No ports")) {
                for (String line : portOutput.split("\n")) {
                    String[] parts = line.trim().split("->");
                    if (parts.length >= 2) {
                        String hostPort = parts[1].split("\\s+")[0];
                        String protocol = parts[0].contains("/tcp") ? "http" : "https";
                        String url = protocol + "://localhost:" + hostPort;
                        String key = image.contains("nacos") ? "console"
                                : image.contains("mysql") ? "jdbc"
                                : image.contains("redis") ? "redis"
                                : "service-" + parts[0].split("/")[0];
                        endpoints.put(key, url);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[collectContainerEndpoints] Failed to get ports for {}: {}", containerName, e.getMessage());
        }
        return endpoints;
    }

    private Mono<Void> executeDockerStop(String envId, ServiceInstance instance) {
        return Mono.fromCallable(() -> {
            String containerName = "svc-" + envId + "-" + instance.getInstanceId().substring(0, 8);
            log.info("[stopContainer] Stopping: {}", containerName);
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> executeDockerStart(Environment environment, ServiceInstance instance) {
        return Mono.fromCallable(() -> {
            String containerName = "svc-" + environment.getIdValue() + "-" + instance.getInstanceId().substring(0, 8);
            log.info("[startContainer] Starting: {}", containerName);
            ProcessBuilder pb = new ProcessBuilder("docker", "start", containerName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("docker start timed out for " + containerName);
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
