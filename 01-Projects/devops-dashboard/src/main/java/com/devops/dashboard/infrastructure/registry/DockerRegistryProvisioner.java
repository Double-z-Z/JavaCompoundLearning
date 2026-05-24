package com.devops.dashboard.infrastructure.registry;

import com.devops.dashboard.domain.registry.RegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Docker Registry 部署器。
 *
 * <p>负责：自签 TLS 证书生成 → Registry 容器启动 → 状态验证。</p>
 */
public class DockerRegistryProvisioner {

    private static final Logger log = LoggerFactory.getLogger(DockerRegistryProvisioner.class);
    private static final String REGISTRY_IMAGE = "registry:2";

    public record RegistryDeployResult(
        String registryUrl,
        String caCertificate,
        boolean success,
        String message
    ) {}

    /**
     * 部署 Docker Registry 容器。
     */
    public Mono<RegistryDeployResult> deploy(RegistryConfig config) {
        return Mono.fromCallable(() -> {
            log.info("[Registry] Deploying registry on host={}, url={}", config.hostId(), config.getUrl());

            // 1. 生成自签证书
            Path certDir = Path.of(config.dataDir(), "certs");
            Files.createDirectories(certDir);
            generateSelfSignedCert(config.hostname(), certDir);

            // 2. 生成 htpasswd（可选，基础认证）
            Path authDir = Path.of(config.dataDir(), "auth");
            Files.createDirectories(authDir);

            // 3. 停止旧容器（幂等）
            stopExistingRegistry();

            // 4. 启动 Registry 容器
            startRegistryContainer(config, certDir, authDir);

            // 5. 验证 Registry 可访问
            boolean healthy = waitForRegistry(config, 15);

            // 6. 读取 CA 证书
            String caCert = Files.readString(certDir.resolve("ca.crt"));

            return new RegistryDeployResult(
                config.getUrl(),
                caCert,
                healthy,
                healthy ? "Registry deployed successfully" : "Registry started but health check failed"
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void generateSelfSignedCert(String hostname, Path certDir) throws Exception {
        Path caKey = certDir.resolve("ca.key");
        Path caCrt = certDir.resolve("ca.crt");
        Path serverKey = certDir.resolve("server.key");
        Path serverCrt = certDir.resolve("server.crt");

        // 生成 CA 密钥和证书
        exec("openssl", "genrsa", "-out", caKey.toString(), "2048");
        exec("openssl", "req", "-new", "-x509", "-days", "3650",
            "-key", caKey.toString(),
            "-out", caCrt.toString(),
            "-subj", "/CN=Docker Registry CA/O=DevOps Dashboard"
        );

        // 生成服务器密钥和 CSR
        exec("openssl", "genrsa", "-out", serverKey.toString(), "2048");
        exec("openssl", "req", "-new",
            "-key", serverKey.toString(),
            "-out", certDir.resolve("server.csr").toString(),
            "-subj", "/CN=" + hostname
        );

        // 用 CA 签发服务器证书（含 SAN）
        String extFile = certDir.resolve("extfile.cnf").toString();
        Files.writeString(Path.of(extFile),
            "subjectAltName = DNS:" + hostname + "\n");

        exec("openssl", "x509", "-req", "-days", "3650",
            "-in", certDir.resolve("server.csr").toString(),
            "-CA", caCrt.toString(),
            "-CAkey", caKey.toString(),
            "-CAcreateserial",
            "-out", serverCrt.toString(),
            "-extfile", extFile
        );

        log.info("[Registry] Self-signed certs generated for {}", hostname);
    }

    private void stopExistingRegistry() throws Exception {
        try {
            Process p = new ProcessBuilder("docker", "stop", "devops-registry")
                .redirectErrorStream(true).start();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        try {
            Process p = new ProcessBuilder("docker", "rm", "devops-registry")
                .redirectErrorStream(true).start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    private void startRegistryContainer(RegistryConfig config, Path certDir, Path authDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "run", "-d",
            "--name", "devops-registry",
            "--restart", "unless-stopped",
            "-p", config.port() + ":5000",
            "-v", config.dataDir() + ":/var/lib/registry",
            "-v", certDir.toString() + ":/certs",
            "-e", "REGISTRY_HTTP_TLS_CERTIFICATE=/certs/server.crt",
            "-e", "REGISTRY_HTTP_TLS_KEY=/certs/server.key",
            REGISTRY_IMAGE
        );
        pb.redirectErrorStream(true);

        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Registry container start timed out");
        }
        if (p.exitValue() != 0) {
            String error = new String(p.getInputStream().readAllBytes());
            throw new RuntimeException("Registry container start failed: " + error);
        }
        log.info("[Registry] Container started: devops-registry");
    }

    private boolean waitForRegistry(RegistryConfig config, int maxRetries) throws Exception {
        for (int i = 0; i < maxRetries; i++) {
            try {
                Process p = new ProcessBuilder(
                    "curl", "-sk", "https://" + config.getUrl() + "/v2/_catalog"
                ).redirectErrorStream(true).start();
                p.waitFor(3, TimeUnit.SECONDS);
                if (p.exitValue() == 0) {
                    log.info("[Registry] Health check passed on attempt {}", i + 1);
                    return true;
                }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        return false;
    }

    private void exec(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Command timed out: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            String err = new String(p.getInputStream().readAllBytes());
            throw new RuntimeException("Command failed: " + String.join(" ", cmd) + "\n" + err);
        }
    }
}
