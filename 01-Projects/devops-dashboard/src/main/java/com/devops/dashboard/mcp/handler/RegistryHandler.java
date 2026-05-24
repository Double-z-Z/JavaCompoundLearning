package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.domain.host.HostAccess;
import com.devops.dashboard.domain.host.HostId;
import com.devops.dashboard.domain.registry.RegistryConfig;
import com.devops.dashboard.infrastructure.registry.DockerRegistryProvisioner;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * Registry 管理 MCP Handler（V3 新增）。
 *
 * <p>提供私有 Docker Registry 的部署和证书分发能力。</p>
 */
@Component
public class RegistryHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(RegistryHandler.class);

    private final HostService hostService;

    public RegistryHandler(McpExceptionTranslator errorTranslator, HostService hostService) {
        super(errorTranslator);
        this.hostService = hostService;
    }

    /**
     * 部署私有 Docker Registry（MCP Tool: {@code setup_registry}）。
     *
     * @param hostId   部署 Registry 的主机 ID
     * @param hostname Registry 访问域名
     * @return JSON 格式的部署结果（含 CA 证书）
     */
    public Mono<String> setupRegistry(String hostId, String hostname) {
        log.info("MCP Tool [setup_registry]: hostId={}, hostname={}", hostId, hostname);

        RegistryConfig config = RegistryConfig.of(hostId, hostname);
        DockerRegistryProvisioner provisioner = new DockerRegistryProvisioner();

        return handleAsync(
            provisioner.deploy(config)
                .map(result -> result)
        );
    }

    /**
     * 安装 Registry CA 证书到目标主机（MCP Tool: {@code trust_registry}）。
     *
     * @param hostId      目标主机 ID
     * @param registryUrl Registry 地址（host:port）
     * @param caCert      CA 证书 PEM 内容
     * @return JSON 格式的操作结果
     */
    public Mono<String> trustRegistry(String hostId, String registryUrl, String caCert) {
        log.info("MCP Tool [trust_registry]: hostId={}, registryUrl={}", hostId, registryUrl);

        return handleAsync(
            Mono.fromCallable(() -> {
                String registryHost = registryUrl.contains(":")
                    ? registryUrl.substring(0, registryUrl.indexOf(":"))
                    : registryUrl;

                HostId hid = HostId.of(hostId);
                installCertOnHost(hid, registryHost, caCert);

                return new TrustResult(true, "CA certificate installed on " + hostId);
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    private void installCertOnHost(HostId hostId, String registryHost, String caCert) throws Exception {
        // V3 ADR-022: 统一走 SSH 确保操作审计，禁止本地文件系统直写
        var hostAccess = hostService.getHostAccess(hostId);
        String remoteCmd = String.format(
            "mkdir -p /etc/docker/certs.d/%s && cat > /etc/docker/certs.d/%s/ca.crt << 'CERT_EOF'\n%s\nCERT_EOF",
            registryHost, registryHost, caCert
        );

        ProcessBuilder pb = new ProcessBuilder(
            "ssh", "-o", "StrictHostKeyChecking=accept-new",
            "-p", String.valueOf(hostAccess.getSshPort()),
            hostAccess.getUser() + "@" + hostAccess.getSshHost(),
            remoteCmd
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished || p.exitValue() != 0) {
            String error = new String(p.getInputStream().readAllBytes());
            throw new RuntimeException("SSH cert install failed: " + error);
        }
        log.info("[trust_registry] CA cert installed on {} via SSH", hostId);
    }

    private record TrustResult(boolean success, String message) {}
}
