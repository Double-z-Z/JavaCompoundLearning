package com.example.order.util;

import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * @deprecated 项目已接入 Spring Boot，Mapper 通过 @Autowired 注入。
 *             仅 BATCH 模式等特殊场景仍需 openSession。
 */
@Deprecated
public class SqlSessionUtil {
    private static volatile SqlSessionFactory factory;

    private SqlSessionUtil() {}

    public static SqlSessionFactory getFactory() {
        if (factory == null) {
            synchronized (SqlSessionUtil.class) {
                if (factory == null) {
                    try (InputStream is = Resources.getResourceAsStream("mybatis-config.xml")) {
                        // MP 的 Builder 会注入 BaseMapper 的 SQL
                        factory = new MybatisSqlSessionFactoryBuilder().build(is);

                        // 注册 MP 拦截器链（顺序：分页 → 乐观锁 → 多租户）
                        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
                        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
                        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
                        interceptor.addInnerInterceptor(
                                new TenantLineInnerInterceptor(new ContextTenantLineHandler()));
                        factory.getConfiguration().addInterceptor(interceptor);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to init SqlSessionFactory", e);
                    }
                }
            }
        }
        return factory;
    }
}
