package com.devops.dashboard.domain.registry;

/**
 * Docker Registry 部署配置（Value Object）。
 *
 * @param hostId    运行 Registry 的主机 ID
 * @param port      监听端口，默认 5000
 * @param hostname  Registry 访问域名（用于 TLS 证书 SAN），如 "registry.devops.local"
 * @param dataDir   Registry 数据持久化目录
 */
public record RegistryConfig(
    String hostId,
    int port,
    String hostname,
    String dataDir
) {
    public static RegistryConfig of(String hostId, String hostname) {
        return new RegistryConfig(hostId, 5000, hostname, System.getProperty("user.home") + "/.devops/registry");
    }

    public String getUrl() {
        return hostname + ":" + port;
    }
}
