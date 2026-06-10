package com.mvp.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductQueryRequest {
    private String goodsName;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}