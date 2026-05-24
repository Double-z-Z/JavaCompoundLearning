package com.devops.dashboard.infrastructure.environment;

import com.devops.dashboard.domain.environment.Environment;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.environment.EnvironmentProvisioner;
import com.devops.dashboard.domain.environment.EnvironmentSpec;
import com.devops.dashboard.domain.environment.EnvironmentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Docker Compose 环境基础设施提供者
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerComposeEnvironment implements EnvironmentProvisioner {

    private static final String COMPOSE_FILE_PREFIX = "devops-env-";
    private static final String DOCKER_NOT_AVAILABLE = "Docker not available";

    @Override
    public Mono<Environment> provision(EnvironmentSpec spec) {
        return Mono.fromCallable(() -> {
            EnvironmentId envId = EnvironmentId.generate();
            String envIdValue = envId.getValue();

            Path composeDir = Paths.get(".docker/environments/" + envIdValue);
            Path composeFile = composeDir.resolve("docker-compose.yml");

            Files.createDirectories(composeDir);
            Files.writeString(composeFile, generateComposeContent(spec, envIdValue));

            try {
                int exitCode = new ProcessBuilder()
                        .command("docker", "compose", "-f", composeFile.toString(), "up", "-d")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();

                if (exitCode != 0) {
                    throw new RuntimeException("docker-compose up failed with exit code " + exitCode);
                }
            } catch (Exception e) {
                cleanupComposeDirectory(envIdValue);
                throw e;
            }

            log.info("Environment provisioned: {}", envIdValue);

            return Environment.provisioned(envIdValue, spec);
        });
    }

    @Override
    public Mono<Map<String, String>> deployService(EnvironmentId envId, String serviceName, String image, String instanceId) {
        return checkDockerAvailable()
                .flatMap(available -> {
                    if (!available) {
                        return Mono.error(new RuntimeException(DOCKER_NOT_AVAILABLE));
                    }
                    return pullImage(image)
                            .then(doDockerRun(envId, serviceName, image, instanceId));
                });
    }

    @Override
    public Mono<Void> stopService(EnvironmentId envId, String instanceId) {
        return Mono.fromCallable(() -> {
            String name = containerName(envId.getValue(), instanceId);
            log.info("[stopService] Stopping container: {}", name);
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> startService(EnvironmentId envId, String instanceId, String image) {
        return Mono.fromCallable(() -> {
            String name = containerName(envId.getValue(), instanceId);
            log.info("[startService] Starting container: {}", name);
            ProcessBuilder pb = new ProcessBuilder("docker", "start", name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("docker start timed out for " + name);
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> teardown(EnvironmentId id) {
        return Mono.fromCallable(() -> {
            String envIdValue = id.getValue();
            String containerName = COMPOSE_FILE_PREFIX + envIdValue;

            // 先按 label 批量清理所有服务容器（svc-*），防止 docker run 创建的残留
            cleanupContainersByLabel(envIdValue);

            Path composeFile = Paths.get(".docker/environments/" + envIdValue + "/docker-compose.yml");
            if (Files.exists(composeFile)) {
                try {
                    Process process = new ProcessBuilder()
                            .command("docker", "compose", "-f", composeFile.toString(), "down", "--timeout", "10")
                            .redirectErrorStream(true)
                            .start();

                    boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        log.warn("[teardown] docker compose down timeout, force killing for {}", id.getValue());
                    }

                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        log.info("Environment destroyed via compose: {}", id.getValue());
                        cleanupComposeDirectory(id.getValue());
                        return null;
                    }
                } catch (IOException | InterruptedException e) {
                    log.warn("Compose down failed, falling back to container removal: {}", e.getMessage());
                }
            }

            try {
                Process process = new ProcessBuilder()
                        .command("docker", "rm", "-f", containerName)
                        .redirectErrorStream(true)
                        .start();

                boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }

                log.info("Environment destroyed via container removal: {}", id.getValue());
            } catch (IOException | InterruptedException e) {
                log.error("Failed to destroy environment: {}", id.getValue(), e);
                throw new RuntimeException("Failed to destroy environment " + id.getValue(), e);
            }
            cleanupComposeDirectory(id.getValue());
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void cleanupContainersByLabel(String envIdValue) {
        try {
            Process listProcess = new ProcessBuilder()
                    .command("docker", "ps", "-aq", "--filter", "label=devops.env=" + envIdValue)
                    .redirectErrorStream(true)
                    .start();
            listProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            String containerIds = new String(listProcess.getInputStream().readAllBytes()).trim();

            if (!containerIds.isEmpty()) {
                String[] ids = containerIds.split("\n");
                log.info("[teardown] Removing {} service containers for env={}", ids.length, envIdValue);

                var command = new java.util.ArrayList<String>();
                command.add("docker");
                command.add("rm");
                command.add("-f");
                command.addAll(java.util.Arrays.asList(ids));

                Process rmProcess = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                rmProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("[teardown] Label-based container cleanup failed for {}: {}", envIdValue, e.getMessage());
        }
    }

    private void cleanupComposeDirectory(String envIdValue) {
        try {
            Path composeDir = Paths.get(".docker/environments/" + envIdValue);
            if (Files.exists(composeDir)) {
                Files.walk(composeDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                        });
                log.debug("Cleaned up compose directory for {}", envIdValue);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup compose directory for {}: {}", envIdValue, e.getMessage());
        }
    }

    @Override
    public Mono<EnvironmentStatus> checkStatus(EnvironmentId id) {
        return Mono.fromCallable(() -> {
            try {
                var process = new ProcessBuilder()
                        .command("docker", "ps", "--filter", "name=" + COMPOSE_FILE_PREFIX + id.getValue(), "--format", "{{.Status}}")
                        .redirectErrorStream(true)
                        .start();

                boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("[checkStatus] Timeout checking status for {}", id.getValue());
                    return EnvironmentStatus.ERROR;
                }

                String output = new String(process.getInputStream().readAllBytes()).trim();
                if (process.waitFor() != 0 || output.isBlank()) {
                    return EnvironmentStatus.DESTROYED;
                }

                if (output.contains("Up")) {
                    return EnvironmentStatus.RUNNING;
                }
                if (output.contains("Exited") || output.contains("Created")) {
                    return EnvironmentStatus.DESTROYED;
                }
                return EnvironmentStatus.ERROR;
            } catch (IOException | InterruptedException e) {
                log.warn("[checkStatus] Failed to check status for {}: {}", id.getValue(), e.getMessage());
                return EnvironmentStatus.ERROR;
            }
        });
    }

    private String containerName(String envIdValue, String instanceId) {
        return "svc-" + envIdValue + "-" + instanceId.substring(0, 8);
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

    private Mono<Void> pullImage(String image) {
        return Mono.fromCallable(() -> {
            log.info("[pullImage] docker pull {}", image);
            ProcessBuilder pb = new ProcessBuilder("docker", "pull", image);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("docker pull timed out after 120s: " + image);
            }
            if (p.exitValue() != 0) {
                String error = new String(p.getInputStream().readAllBytes());
                throw new RuntimeException("docker pull failed (exit=" + p.exitValue() + "): " + error);
            }
            log.info("[pullImage] Successfully pulled {}", image);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Map<String, String>> doDockerRun(EnvironmentId envId, String serviceName, String image, String instanceId) {
        return Mono.fromCallable(() -> {
            String cName = containerName(envId.getValue(), instanceId);
            log.info("[deployContainer] docker run -d --name {} {}", cName, image);

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "-d",
                    "--name", cName,
                    "--label", "devops.env=" + envId.getValue(),
                    "--label", "devops.service=" + serviceName,
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

            return collectContainerEndpoints(cName, image);
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

    private String generateComposeContent(EnvironmentSpec spec, String envId) {
        return """
                version: '3.8'
                services:
                  environment:
                    container_name: %s%s
                    image: %s
                    restart: unless-stopped
                """.formatted(
                COMPOSE_FILE_PREFIX, envId,
                resolveImage(spec.getEnvironmentType())
        );
    }

    private String resolveImage(com.devops.dashboard.domain.environment.EnvironmentType type) {
        return switch (type) {
            case DEV -> "nginx:latest";
            case TEST, STAGING -> "nginx:alpine";
            case PROD -> "nginx:latest";
            case EXPERIMENT -> "ubuntu:22.04";
        };
    }
}