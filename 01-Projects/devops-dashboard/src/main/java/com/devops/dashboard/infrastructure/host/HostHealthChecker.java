package com.devops.dashboard.infrastructure.host;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.domain.host.HostAccess;
import com.devops.dashboard.domain.host.HostHealthStatus;
import com.devops.dashboard.domain.host.HostId;
import com.devops.dashboard.infrastructure.loadgen.SshCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 主机运行时健康检查器。
 *
 * <p>通过 SSH 定期探活所有 TARGET 角色主机，将结果写入 {@link HostHealthCache}。</p>
 */
@Component
public class HostHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HostHealthChecker.class);

    private final HostService hostService;
    private final HostHealthCache healthCache;
    private final SshCommandExecutor sshExecutor;

    public HostHealthChecker(HostService hostService,
                             HostHealthCache healthCache,
                             SshCommandExecutor sshExecutor) {
        this.hostService = hostService;
        this.healthCache = healthCache;
        this.sshExecutor = sshExecutor;
    }

    @Scheduled(fixedRateString = "${devops.health-check.interval-ms:60000}",
               initialDelayString = "${devops.health-check.initial-delay-ms:10000}")
    public void probeAll() {
        HostTopology topology = hostService.getTopology();
        int probed = 0;

        for (var host : topology.getHosts()) {
            if (!host.isTarget()) continue;

            HostAccess access;
            try {
                access = hostService.getHostAccess(HostId.of(host.id()));
            } catch (Exception e) {
                healthCache.update(host.id(), HostHealthStatus.UNKNOWN);
                continue;
            }

            if (access == null || access.getSshHost() == null) {
                healthCache.update(host.id(), HostHealthStatus.UNKNOWN);
                continue;
            }

            try {
                var result = sshExecutor.execute(access, "echo ok", 10);
                if (result.isSuccess()) {
                    healthCache.update(host.id(), HostHealthStatus.HEALTHY);
                } else {
                    log.debug("[HealthCheck] {} unreachable: {}", host.id(), result.getStderr());
                    healthCache.update(host.id(), HostHealthStatus.UNREACHABLE);
                }
                probed++;
            } catch (Exception e) {
                log.debug("[HealthCheck] {} probe failed: {}", host.id(), e.getMessage());
                healthCache.update(host.id(), HostHealthStatus.UNREACHABLE);
            }
        }

        log.debug("[HealthCheck] Probed {} hosts, cache size: {}", probed, healthCache.size());
    }
}
