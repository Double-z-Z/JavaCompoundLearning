package com.example.order.migration.interceptor;
import com.example.order.migration.MigrationContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 灰度读上下文拦截器 — 从请求参数中提取 userId 放入 ThreadLocal.
 *
 * MigrationRoutingDataSource.determineCurrentLookupKey() 通过
 * MigrationContext.getUserId() 获取当前请求的 userId, 判断是否命中灰度.
 */
@Component
@ConditionalOnProperty(name = "migration.active", havingValue = "true")
public class MigrationContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) {
        String userId = request.getParameter("userId");
        if (userId != null && !userId.isEmpty()) {
            MigrationContext.setUserId(Long.parseLong(userId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        MigrationContext.clear();
    }
}
