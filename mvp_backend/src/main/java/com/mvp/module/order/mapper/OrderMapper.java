package com.mvp.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mvp.module.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM seckill_order WHERE user_id = #{userId} AND goods_id = #{goodsId} LIMIT 1")
    Order findByUserIdAndGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    @Select("SELECT * FROM seckill_order WHERE status = 0 AND pay_expire_time < #{now} ORDER BY pay_expire_time ASC LIMIT #{limit}")
    List<Order> selectExpiredOrders(@Param("now") Long now, @Param("limit") int limit);
}