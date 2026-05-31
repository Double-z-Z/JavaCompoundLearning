package com.example.order.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MP 自动填充处理器。
 * INSERT 时自动填 createTime / updateTime，
 * UPDATE 时自动填 updateTime。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.setFieldValByName("createTime", now, metaObject);
        this.setFieldValByName("updateTime", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // setFieldValByName 直接覆写，不检查已有值
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
