package com.devops.dashboard.infrastructure.environment;

import com.devops.dashboard.domain.environment.Environment;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.environment.EnvironmentSpec;
import com.devops.dashboard.domain.environment.EnvironmentStatus;
import com.devops.dashboard.domain.environment.EnvironmentType;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DockerComposeEnvironment 单元测试")
@ExtendWith(MockitoExtension.class)
class DockerComposeEnvironmentTest {

    @InjectMocks
    private DockerComposeEnvironment dockerComposeEnvironment;

    private final List<String> createdContainerIds = new ArrayList<>();

    private EnvironmentSpec createTestSpec(EnvironmentType type) {
        return EnvironmentSpec.builder()
            .type(type)
            .resourceQuota(ResourceQuota.development())
            .targetNodes(List.of())
            .build();
    }

    @AfterEach
    void cleanup() {
        createdContainerIds.forEach(id -> {
            try {
                dockerComposeEnvironment.teardown(EnvironmentId.of(id)).block();
            } catch (Exception ignored) {}
        });
        createdContainerIds.clear();
    }

    private boolean containerExists(String containerName) throws Exception {
        var process = new ProcessBuilder()
            .command("docker", "ps", "--filter", "name=" + containerName, "-q")
            .start();
        boolean exists = !new String(process.getInputStream().readAllBytes()).trim().isEmpty();
        process.waitFor();
        return exists;
    }

    @Nested
    @DisplayName("provision() 环境创建")
    class Provision {

        @Test
        @DisplayName("应返回Environment实体")
        void shouldReturnEnvironment() {
            EnvironmentSpec spec = createTestSpec(EnvironmentType.DEV);

            Environment env = dockerComposeEnvironment.provision(spec).block();
            createdContainerIds.add(env.getId().getValue());

            assertThat(env).isNotNull();
            assertThat(env.getId()).isNotNull();
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.RUNNING);
        }

        @Test
        @DisplayName("创建后docker命令确认容器存在")
        void shouldCreateContainerVisibleViaDocker() throws Exception {
            Environment env = dockerComposeEnvironment.provision(createTestSpec(EnvironmentType.DEV)).block();
            createdContainerIds.add(env.getId().getValue());
            String containerName = "devops-env-" + env.getId().getValue();

            // 直接用docker命令验证容器存在
            assertThat(containerExists(containerName)).isTrue();
        }
    }

    @Nested
    @DisplayName("checkStatus() 状态检查")
    class CheckStatus {

        @Test
        @DisplayName("不存在环境返回NOT_FOUND")
        void shouldReturnNotFoundForMissingEnv() {
            EnvironmentId id = EnvironmentId.generate();

            dockerComposeEnvironment.checkStatus(id)
                .as(StepVerifier::create)
                .expectNext(EnvironmentStatus.NOT_FOUND)
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("teardown() 环境销毁")
    class Teardown {

        @Test
        @DisplayName("docker命令确认容器被删除")
        void shouldDestroyContainerViaDockerCommand() throws Exception {
            // 1. 创建环境
            Environment env = dockerComposeEnvironment.provision(createTestSpec(EnvironmentType.DEV)).block();
            createdContainerIds.add(env.getId().getValue());
            String containerName = "devops-env-" + env.getId().getValue();

            // 2. 确认容器存在
            assertThat(containerExists(containerName)).isTrue();

            // 3. 销毁
            dockerComposeEnvironment.teardown(env.getId()).block();

            // 4. 直接用docker命令验证容器不存在
            assertThat(containerExists(containerName)).isFalse();
        }

        @Test
        @DisplayName("不存在compose文件时应仍能清理容器")
        void shouldCleanupEvenWithoutComposeFile() {
            EnvironmentId id = EnvironmentId.generate();

            dockerComposeEnvironment.teardown(id)
                .as(StepVerifier::create)
                .verifyComplete();
        }
    }
}