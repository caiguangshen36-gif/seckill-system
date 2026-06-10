package com.mvp.common.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long goodsId;
    private Integer changeAmount;
    private String operationType;
    private Long orderId;
    private Long createTime;
}