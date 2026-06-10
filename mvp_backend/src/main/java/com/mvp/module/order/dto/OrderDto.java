package com.mvp.module.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDto {
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;
    
    @NotNull(message = "订单价格不能为空")
    @Positive(message = "订单价格必须为正数")
    private BigDecimal orderPrice;
}