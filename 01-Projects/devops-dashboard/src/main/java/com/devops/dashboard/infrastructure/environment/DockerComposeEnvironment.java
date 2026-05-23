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

/**
 * Docker Compose 环境基础设施提供者
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerComposeEnvironment implements EnvironmentProvisioner {

    private static final String COMPOSE_FILE_PREFIX = "devops-env-";

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
    public Mono<Void> teardown(EnvironmentId id) {
        return Mono.fromCallable(() -> {
            String containerName = COMPOSE_FILE_PREFIX + id.getValue();

            Path composeFile = Paths.get(".docker/environments/" + id.getValue() + "/docker-compose.yml");
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
                resolveImage(spec.getType())
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