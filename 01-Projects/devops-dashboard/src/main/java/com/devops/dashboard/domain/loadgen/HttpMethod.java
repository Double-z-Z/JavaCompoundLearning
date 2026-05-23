package com.devops.dashboard.domain.loadgen;

import java.time.Duration;
import java.util.Objects;

/**
 * HTTP 请求方法枚举，用于压测目标 URL 的请求类型定义。
 *
 * @see com.devops.dashboard.domain.loadgen.LoadTestSpec 压测规格说明
 */
public enum HttpMethod {

    GET("get"),
    POST("post"),
    PUT("put"),
    DELETE("delete");

    private final String code;

    HttpMethod(String code) {
        this.code = code;
    }

    public String getCode() { return code; }

    public static HttpMethod fromString(String value) {
        if (value == null) return GET;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GET;
        }
    }
}
