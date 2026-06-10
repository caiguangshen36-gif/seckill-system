package com.mvp.common.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long goodsId;
    private String goodsName;
    private BigDecimal orderPrice;
    private Integer status;
    private Long createTime;
    private String messageType;
}