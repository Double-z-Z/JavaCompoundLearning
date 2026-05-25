package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.domain.host.Capability;
import com.devops.dashboard.domain.host.HostAccess;
import com.devops.dashboard.domain.host.HostId;
import com.devops.dashboard.infrastructure.host.RuntimeCapabilityStore;
import com.devops.dashboard.infrastructure.loadgen.SshCommandExecutor;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主机能力管理 MCP Handler（V3 新增）。
 *
 * <p>提供远程 Docker 安装、主机升级等运行时能力管理 Tool。</p>
 */
@Component
public class HostCapabilityHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(HostCapabilityHandler.class);

    private static final String DOCKER_INSTALL_SCRIPT =
            "curl -fsSL https://get.docker.com | sh && systemctl enable --now docker 2>&1";

    private final HostService hostService;
    private final RuntimeCapabilityStore runtimeCapabilityStore;
    private final SshCommandExecutor sshExecutor;

    public HostCapabilityHandler(McpExceptionTranslator errorTranslator,
                                 HostService hostService,
                                 RuntimeCapabilityStore runtimeCapabilityStore,
                                 SshCommandExecutor sshExecutor) {
        super(errorTranslator);
        this.hostService = hostService;
        this.runtimeCapabilityStore = runtimeCapabilityStore;
        this.sshExecutor = sshExecutor;
    }

    /**
     * 远程安装 Docker（MCP Tool: {@code host_install_docker}）。
     *
     * @param hostId         目标主机 ID
     * @param timeoutSeconds 安装超时秒数，默认 300
     * @return JSON 格式的安装结果
     */
    public Mono<Map<String, Object>> installDocker(String hostId, int timeoutSeconds) {
        log.info("MCP Tool [host_install_docker]: hostId={}, timeout={}s", hostId, timeoutSeconds);

        return Mono.fromCallable(() -> {
            HostAccess access = hostService.getHostAccess(HostId.of(hostId));
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : 300;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);

            // 1. 执行安装
            log.info("[host_install_docker] Installing Docker on {} via SSH", hostId);
            var installResult = sshExecutor.execute(access, DOCKER_INSTALL_SCRIPT, timeout);
            result.put("installExitCode", installResult.getExitCode());
            result.put("installOutput", installResult.getStdout());

            if (!installResult.isSuccess()) {
                result.put("success", false);
                result.put("message", "Docker installation failed: " + installResult.getStderr());
                return result;
            }

            // 2. 验证
            var verifyResult = sshExecutor.execute(access, "docker --version 2>&1", 15);
            result.put("dockerVersion", verifyResult.getStdout().trim());

            if (!verifyResult.isSuccess()) {
                result.put("success", false);
                result.put("message", "Docker installed but verification failed: " + verifyResult.getStderr());
                return result;
            }

            // 3. 更新运行时能力缓存
            runtimeCapabilityStore.add(hostId, Capability.DOCKER);
            log.info("[host_install_docker] Docker installed and capability registered for {}", hostId);

            result.put("success", true);
            result.put("message", "Docker installed successfully on " + hostId);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 远程主机升级（MCP Tool: {@code host_upgrade}）。
     *
     * @param hostId    目标主机 ID
     * @param target    升级目标: docker | system
     * @param timeoutSeconds 升级超时秒数，默认 600
     * @return JSON 格式的升级结果
     */
    public Mono<Map<String, Object>> upgrade(String hostId, String target, int timeoutSeconds) {
        log.info("MCP Tool [host_upgrade]: hostId={}, target={}", hostId, target);

        return Mono.fromCallable(() -> {
            HostAccess access = hostService.getHostAccess(HostId.of(hostId));
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : 600;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("target", target);

            String upgradeCmd = switch (target.toLowerCase()) {
                case "docker" -> "dnf update -y docker-ce docker-ce-cli containerd.io 2>&1";
                case "system" -> "dnf upgrade -y 2>&1";
                default -> throw new IllegalArgumentException("Unsupported upgrade target: " + target);
            };

            var upgradeResult = sshExecutor.execute(access, upgradeCmd, timeout);
            result.put("upgradeExitCode", upgradeResult.getExitCode());
            result.put("upgradeOutput", upgradeResult.getStdout());
            result.put("success", upgradeResult.isSuccess());
            result.put("message", upgradeResult.isSuccess()
                    ? target + " upgrade completed"
                    : "Upgrade failed: " + upgradeResult.getStderr());

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
