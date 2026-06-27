package com.mvp.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mvp.module.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    
    @Select("SELECT * FROM seckill_order WHERE user_id = #{userId} AND goods_id = #{goodsId} LIMIT 1")
    Order findByUserIdAndGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);
}