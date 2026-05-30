package com.example.order.util;

import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.io.InputStream;

public class SqlSessionUtil {
    private static volatile SqlSessionFactory factory;

    private SqlSessionUtil() {}

    public static SqlSessionFactory getFactory() {
        if (factory == null) {
            synchronized (SqlSessionUtil.class) {
                if (factory == null) {
                    try (InputStream is = Resources.getResourceAsStream("mybatis-config.xml")) {
                        // 关键：用 MP 的 Builder 替换原生 Builder
                        // MP 的 Configuration 会注入 BaseMapper 的 SQL
                        factory = new MybatisSqlSessionFactoryBuilder().build(is);

                        // 注册分页插件
                        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
                        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
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
