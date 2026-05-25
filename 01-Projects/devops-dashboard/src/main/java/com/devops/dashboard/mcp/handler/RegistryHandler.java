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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, RegistryState> deployedRegistries = new ConcurrentHashMap<>();

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

        // 幂等：已部署则直接返回
        RegistryState existing = deployedRegistries.get(hostId);
        if (existing != null) {
            log.info("[setup_registry] Registry already deployed on hostId={}, returning cached state", hostId);
            Map<String, Object> cached = new LinkedHashMap<>();
            cached.put("registryUrl", existing.registryUrl());
            cached.put("caCertificate", existing.caCertificate());
            cached.put("success", true);
            cached.put("message", "Registry already deployed (cached)");
            return handleAsync(Mono.just(cached));
        }

        RegistryConfig config = RegistryConfig.of(hostId, hostname);
        DockerRegistryProvisioner provisioner = new DockerRegistryProvisioner();

        return handleAsync(
            provisioner.deploy(config)
                .map(result -> {
                    if (result.success()) {
                        deployedRegistries.put(hostId, new RegistryState(
                                hostId, hostname, result.registryUrl(),
                                result.caCertificate(), Instant.now()));
                    }
                    return result;
                })
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

        var sshArgs = new java.util.ArrayList<String>();
        sshArgs.add("ssh");
        sshArgs.add("-o");
        sshArgs.add("StrictHostKeyChecking=accept-new");
        sshArgs.add("-o");
        sshArgs.add("ConnectTimeout=10");
        sshArgs.add("-p");
        sshArgs.add(String.valueOf(hostAccess.getSshPort()));

        String keyPath = hostAccess.getKeyPath();
        if (keyPath != null && !keyPath.isBlank()) {
            sshArgs.add("-i");
            sshArgs.add(keyPath);
            log.debug("[trust_registry] Using SSH key: {}", keyPath);
        } else {
            log.warn("[trust_registry] No key_path configured for hostId={}. Falling back to default SSH key.", hostId.value());
        }

        sshArgs.add(hostAccess.getUser() + "@" + hostAccess.getSshHost());
        sshArgs.add(remoteCmd);

        ProcessBuilder pb = new ProcessBuilder(sshArgs);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished || p.exitValue() != 0) {
            String error = new String(p.getInputStream().readAllBytes());
            throw new RuntimeException("SSH cert install failed: " + error);
        }
        log.info("[trust_registry] CA cert installed on {} via SSH", hostId);
    }

    /**
     * 查询已部署的 Registry 状态（MCP Tool: {@code registry_status}）。
     *
     * @param hostIdFilter 可选的主机 ID 筛选
     * @return JSON 格式的 Registry 列表
     */
    public Mono<Map<String, Object>> getRegistryStatus(String hostIdFilter) {
        log.debug("MCP Tool [registry_status]: hostIdFilter={}", hostIdFilter);
        return Mono.fromCallable(() -> {
            var result = new LinkedHashMap<String, Object>();
            var registries = new java.util.ArrayList<Map<String, Object>>();
            deployedRegistries.entrySet().stream()
                    .filter(e -> hostIdFilter == null || hostIdFilter.isBlank() || e.getKey().equals(hostIdFilter))
                    .forEach(e -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("hostId", e.getValue().hostId());
                        entry.put("hostname", e.getValue().hostname());
                        entry.put("registryUrl", e.getValue().registryUrl());
                        entry.put("deployedAt", e.getValue().deployedAt().toString());
                        registries.add(entry);
                    });
            result.put("registries", registries);
            result.put("count", registries.size());
            return result;
        });
    }

    private record TrustResult(boolean success, String message) {}

    private record RegistryState(
            String hostId,
            String hostname,
            String registryUrl,
            String caCertificate,
            Instant deployedAt) {}
}
