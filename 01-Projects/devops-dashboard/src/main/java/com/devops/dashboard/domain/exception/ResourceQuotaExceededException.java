package com.devops.dashboard.domain.exception;

public class ResourceQuotaExceededException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public ResourceQuotaExceededException(String resource, String requested, String limit) {
        super(String.format("Resource quota exceeded for %s: requested %s, limit %s",
            resource, requested, limit));
    }
}