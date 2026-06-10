package com.mvp.module.product.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVo {
    private Long id;
    private String goodsName;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private Integer version;
    private Integer status;
    private String statusDesc;
    private Long startTime;
    private Long endTime;
}