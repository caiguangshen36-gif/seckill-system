package com.mvp.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("seckill_goods")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String goodsName;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    @Version
    private Integer version;
    private Integer status;
    private Long startTime;
    private Long endTime;
}