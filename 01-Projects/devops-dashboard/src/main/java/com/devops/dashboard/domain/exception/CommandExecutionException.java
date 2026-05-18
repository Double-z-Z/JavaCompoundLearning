package com.devops.dashboard.domain.exception;

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
