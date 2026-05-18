package com.devops.dashboard.domain.exception;

// === 基础设施相关异常 ===

public class ProviderConnectionException extends DomainException {
    
    public ProviderConnectionException(String providerType, String host) {
        super("Cannot connect to provider " + providerType + " at " + host);
    }
    
    public ProviderConnectionException(String providerType, String host, Throwable cause) {
        super("Cannot connect to provider " + providerType + " at " + host, cause);
    }
}

public class CommandExecutionException extends DomainException {
    
    private final int exitCode;
    private final String command;
    
    public CommandExecutionException(String command, int exitCode, String stderr) {
        super(String.format("Command '%s' failed with exit code %d: %s", command, exitCode, stderr));
        this.exitCode = exitCode;
        this.command = command;
    }
    
    public int getExitCode() {
        return exitCode;
    }
    
    public String getCommand() {
        return command;
    }
}

public class ContainerNotFoundException extends DomainException {
    
    public ContainerNotFoundException(String containerId) {
        super("Container not found: " + containerId);
    }
}
