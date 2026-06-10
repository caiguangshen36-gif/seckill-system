package com.mvp.common.mq.producer;

import com.mvp.common.mq.dto.OrderMessageDTO;
import com.mvp.common.mq.dto.StockMessageDTO;
import com.mvp.common.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 消息生产者
 * 负责发送订单、库存相关消息
 */
@Slf4j
@Component
@ConditionalOnClass(RabbitTemplate.class)
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageConverter messageConverter;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("========================================");
        log.info("[消息队列] RabbitMQ 消息生产者初始化成功");
        log.info("[消息队列] 交换机: {}", RabbitMQConfig.ORDER_EXCHANGE);
        log.info("[消息队列] 队列: {}", RabbitMQConfig.ORDER_CREATE_QUEUE);
        log.info("[消息队列] 死信队列: {}", RabbitMQConfig.ORDER_DEAD_LETTER_QUEUE);
        log.info("[消息队列] 消息转换器: {}", messageConverter.getClass().getSimpleName());
        log.info("========================================");
    }

    /**
     * 发送订单创建消息
     */
    public void sendOrderCreateMessage(OrderMessageDTO message) {
        log.info("发送订单创建消息: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CREATE_ROUTING_KEY,
                message
        );
        log.info("订单创建消息发送成功: orderId={}", message.getOrderId());
    }

    /**
     * 发送订单取消消息
     */
    public void sendOrderCancelMessage(OrderMessageDTO message) {
        log.info("发送订单取消消息: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CANCEL_ROUTING_KEY,
                message
        );
        log.info("订单取消消息发送成功: orderId={}", message.getOrderId());
    }

    /**
     * 发送库存扣减消息
     */
    public void sendStockDecrementMessage(Long goodsId, Integer amount, Long orderId) {
        StockMessageDTO message = StockMessageDTO.builder()
                .goodsId(goodsId)
                .changeAmount(-amount)
                .operationType("DECREMENT")
                .orderId(orderId)
                .createTime(System.currentTimeMillis() / 1000)
                .build();

        log.info("发送库存扣减消息: goodsId={}, amount={}, orderId={}", goodsId, amount, orderId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.STOCK_EXCHANGE,
                RabbitMQConfig.STOCK_UPDATE_ROUTING_KEY,
                message
        );
        log.info("库存扣减消息发送成功: goodsId={}", goodsId);
    }

    /**
     * 发送库存恢复消息
     */
    public void sendStockIncrementMessage(Long goodsId, Integer amount, Long orderId) {
        StockMessageDTO message = StockMessageDTO.builder()
                .goodsId(goodsId)
                .changeAmount(amount)
                .operationType("INCREMENT")
                .orderId(orderId)
                .createTime(System.currentTimeMillis() / 1000)
                .build();

        log.info("发送库存恢复消息: goodsId={}, amount={}, orderId={}", goodsId, amount, orderId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.STOCK_EXCHANGE,
                RabbitMQConfig.STOCK_UPDATE_ROUTING_KEY,
                message
        );
        log.info("库存恢复消息发送成功: goodsId={}", goodsId);
    }

    /**
     * 发送延迟消息（用于订单超时处理）
     * @param message 消息内容
     * @param delaySeconds 延迟秒数
     */
    public void sendDelayedMessage(OrderMessageDTO message, long delaySeconds) {
        log.info("发送延迟消息: orderId={}, delay={}s", message.getOrderId(), delaySeconds);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CREATE_ROUTING_KEY,
                message
        );
        log.info("延迟消息发送成功: orderId={}", message.getOrderId());
    }
}