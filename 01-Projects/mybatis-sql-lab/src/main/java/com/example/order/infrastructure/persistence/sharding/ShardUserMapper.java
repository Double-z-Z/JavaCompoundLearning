package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.infrastructure.persistence.sharding.ShardUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShardUserMapper extends BaseMapper<ShardUser> {
}
