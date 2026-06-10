package com.mvp.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mvp.module.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    
    @Update("UPDATE seckill_goods SET stock_count = stock_count - 1 WHERE id = #{id} AND stock_count > 0")
    int decreaseStock(@Param("id") Long id);
    
    @Update("UPDATE seckill_goods SET stock_count = stock_count + 1 WHERE id = #{id}")
    int incrementStock(@Param("id") Long id);
}