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

            int exitCode = new ProcessBuilder()
                    .command("docker", "compose", "-f", composeFile.toString(), "up", "-d")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("docker-compose up failed with exit code " + exitCode);
            }

            log.info("Environment provisioned: {}", envIdValue);

            return Environment.provisioned(envIdValue, spec);
        });
    }

    @Override
    public Mono<Void> teardown(EnvironmentId id) {
        return Mono.fromRunnable(() -> {
            String containerName = COMPOSE_FILE_PREFIX + id.getValue();

            // 优先用compose文件
            Path composeFile = Paths.get(".docker/environments/" + id.getValue() + "/docker-compose.yml");
            if (Files.exists(composeFile)) {
                try {
                    int exitCode = new ProcessBuilder()
                            .command("docker", "compose", "-f", composeFile.toString(), "down")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor();
                    if (exitCode == 0) {
                        log.info("Environment destroyed via compose: {}", id.getValue());
                        return;
                    }
                } catch (IOException | InterruptedException e) {
                    log.warn("Compose down failed, falling back to container removal: {}", e.getMessage());
                }
            }

            // 回退：直接删除容器
            try {
                new ProcessBuilder()
                        .command("docker", "rm", "-f", containerName)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
                log.info("Environment destroyed via container removal: {}", id.getValue());
            } catch (IOException | InterruptedException e) {
                log.error("Failed to destroy environment: {}", id.getValue(), e);
            }
        });
    }

    @Override
    public Mono<EnvironmentStatus> checkStatus(EnvironmentId id) {
        return Mono.fromCallable(() -> {
            try {
                var process = new ProcessBuilder()
                        .command("docker", "ps", "--filter", "name=" + COMPOSE_FILE_PREFIX + id.getValue(), "--format", "{{.Status}}")
                        .redirectErrorStream(true)
                        .start();

                String output = new String(process.getInputStream().readAllBytes()).trim();
                if (process.waitFor() != 0 || output.isEmpty()) {
                    return EnvironmentStatus.NOT_FOUND;
                }

                return switch (output.charAt(0)) {
                    case 'U' -> EnvironmentStatus.RUNNING;
                    case 'E' -> EnvironmentStatus.STOPPED;
                    default -> EnvironmentStatus.NOT_FOUND;
                };
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return EnvironmentStatus.NOT_FOUND;
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