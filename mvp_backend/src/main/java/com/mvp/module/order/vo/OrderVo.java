package com.mvp.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderVo {
    private Long id;
    private Long userId;
    private Long goodsId;
    private String orderNo;
    private String goodsName;
    private BigDecimal orderPrice;
    private Integer status;
    private Long createTime;
    private Long payExpireTime;
}