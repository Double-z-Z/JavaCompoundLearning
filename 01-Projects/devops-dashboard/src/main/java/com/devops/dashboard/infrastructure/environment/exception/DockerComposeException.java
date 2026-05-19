package com.devops.dashboard.infrastructure.environment.exception;

import com.devops.dashboard.infrastructure.shared.exception.InfrastructureException;

/**
 * Docker Compose 操作失败
 */
public class DockerComposeException extends InfrastructureException {

    private final int exitCode;
    private final String stderr;

    public DockerComposeException(int exitCode, String stderr) {
        super("docker-compose", "编排失败，退出码: " + exitCode);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public DockerComposeException(String message, Throwable cause) {
        super("docker-compose", message, cause);
        this.exitCode = -1;
        this.stderr = "";
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}