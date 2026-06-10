package com.mvp.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDto {
    private Long id;

    @NotBlank(message = "商品名称不能为空")
    private String goodsName;

    @NotNull(message = "秒杀价格不能为空")
    @Positive(message = "秒杀价格必须为正数")
    private BigDecimal seckillPrice;

    @NotNull(message = "库存数量不能为空")
    @Positive(message = "库存数量必须为正数")
    private Integer stockCount;

    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    @NotNull(message = "结束时间不能为空")
    private Long endTime;
}