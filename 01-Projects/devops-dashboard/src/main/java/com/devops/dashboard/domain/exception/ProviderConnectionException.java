package com.devops.dashboard.domain.exception;

public class ProviderConnectionException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public ProviderConnectionException(String providerType, String host) {
        super("Cannot connect to provider " + providerType + " at " + host);
    }

    public ProviderConnectionException(String providerType, String host, Throwable cause) {
        super("Cannot connect to provider " + providerType + " at " + host, cause);
    }
}