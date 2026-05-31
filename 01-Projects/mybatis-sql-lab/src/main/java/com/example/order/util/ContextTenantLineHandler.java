package com.example.order.util;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 从 TenantContextHolder 中取当前租户 ID，
 * 告诉 MP 租户列名是 tenant_id。
 */
public class ContextTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.get();
        // 未设置时默认租户 1，不阻塞已有原生测试
        if (tenantId == null) {
            return new LongValue(1);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // products 表不做租户隔离（公共商品目录）
        return "products".equalsIgnoreCase(tableName);
    }
}
