package com.example.order.migration;

/**
 * 请求级迁移上下文 — 将 userId 跨调用链传递给 DataSource 路由.
 *
 * 用法:
 *   MigrationContext.setUserId(5L);
 *   try { orderRepo.findByUserId(5L); }  // DataSource 层感知 userId=5
 *   finally { MigrationContext.clear(); }
 */
public final class MigrationContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static void clear() { USER_ID.remove(); }
}
