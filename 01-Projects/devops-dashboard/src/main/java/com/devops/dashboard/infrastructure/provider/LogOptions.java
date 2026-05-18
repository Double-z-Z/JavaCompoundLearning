package com.devops.dashboard.infrastructure.provider;

public record LogOptions(
    int tailLines,
    boolean follow,
    String sinceTime,
    String filterKeyword
) {
    public static LogOptions defaults() {
        return new LogOptions(100, false, null, null);
    }

    public static LogOptions tail(int lines) {
        return new LogOptions(lines, false, null, null);
    }

    public static LogOptions follow(int lines) {
        return new LogOptions(lines, true, null, null);
    }
}
