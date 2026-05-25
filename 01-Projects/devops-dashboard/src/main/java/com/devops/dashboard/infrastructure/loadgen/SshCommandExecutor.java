package com.devops.dashboard.infrastructure.loadgen;

import com.devops.dashboard.domain.host.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SSH 远程命令执行器。
 *
 * <p>通过本地 {@code ssh} 命令在远程主机上执行指定命令，捕获 stdout/stderr 和退出码。
 * 作为基础设施层的通用组件，被 {@link SshLoadgenService} 等上层服务复用。</p>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>根据 {@link HostAccess} 构建 ssh 命令行</li>
 *   <li>通过 {@code ProcessBuilder} 启动子进程</li>
 *   <li>读取 stdout/stderr 流（带超时控制）</li>
 *   <li>返回封装的执行结果</li>
 * </ol>
 *
 * @see HostAccess SSH 连接信息
 */
@Component
public class SshCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SshCommandExecutor.class);

    /**
     * 通过 SSH 在远程主机上执行命令。
     *
     * @param access          主机访问信息（SSH 地址、端口、用户、密钥）
     * @param command         要执行的远程命令
     * @param timeoutSeconds  超时秒数，0 表示无超时
     * @return 命令执行结果
     */
    public CommandResult execute(HostAccess access, String command, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        List<String> cmd = buildSshCommand(access, command);

        log.debug("Executing SSH command on {}@{}: {}", access.getUser(), access.getSshHost(), command);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            boolean finished;
            if (timeoutSeconds > 0) {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("SSH command timed out after {}s on {}", timeoutSeconds, access.getSshHost());
                    return new CommandResult(-1, "", "Command timed out after " + timeoutSeconds + "s",
                            System.currentTimeMillis() - startTime);
                }
            } else {
                process.waitFor();
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;

            log.debug("SSH command completed in {}ms with exit code {} on {}",
                    durationMs, exitCode, access.getSshHost());

            return new CommandResult(exitCode, stdout.trim(), stderr.trim(), durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "Interrupted: " + e.getMessage(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Failed to execute SSH command on {}: {}", access.getSshHost(), e.getMessage());
            return new CommandResult(-1, "", "Execution error: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 在本地执行命令（用于健康检查等不需要远程的场景）。
     *
     * @param command         要执行的命令
     * @param timeoutSeconds  超时秒数
     * @return 命令执行结果
     */
    public CommandResult executeLocal(String command, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        log.debug("Executing local command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            if (timeoutSeconds > 0) {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return new CommandResult(-1, "", "Timed out", System.currentTimeMillis() - startTime);
                }
            } else {
                process.waitFor();
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            return new CommandResult(process.exitValue(), stdout.trim(), stderr.trim(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    private List<String> buildSshCommand(HostAccess access, String command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("ConnectTimeout=10");
        cmd.add("-o");
        cmd.add("BatchMode=yes");
        cmd.add("-p");
        cmd.add(String.valueOf(access.getSshPort()));

        if (access.getKeyPath() != null && !access.getKeyPath().isBlank()) {
            cmd.add("-i");
            cmd.add(access.getKeyPath());
        }

        cmd.add(access.getUser() + "@" + access.getSshHost());
        cmd.add(command);
        return cmd;
    }

    private String readStream(java.io.InputStream inputStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 命令执行结果内部类。
     */
    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long durationMs;

        public CommandResult(int exitCode, String stdout, String stderr, long durationMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.durationMs = durationMs;
        }

        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return exitCode == 0; }
    }
}
