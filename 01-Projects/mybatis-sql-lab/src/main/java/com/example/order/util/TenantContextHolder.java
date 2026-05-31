package com.example.order.util;

/**
 * 当前请求的租户上下文（ThreadLocal）。
 * 真实项目中从 JWT / HTTP Header 中提取。
 */
public class TenantContextHolder {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
