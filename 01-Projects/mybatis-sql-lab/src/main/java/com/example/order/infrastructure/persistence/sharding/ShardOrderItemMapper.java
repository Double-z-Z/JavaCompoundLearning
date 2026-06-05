package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.infrastructure.persistence.sharding.ShardOrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShardOrderItemMapper extends BaseMapper<ShardOrderItem> {
}
