package com.mvp.common.mq.consumer;

import com.mvp.common.mq.dto.OrderMessageDTO;
import com.mvp.common.config.RabbitMQConfig;
import com.mvp.module.order.entity.Order;
import com.mvp.module.order.mapper.OrderMapper;
import com.mvp.module.product.mapper.ProductMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单消息消费者
 */
@Slf4j
@Component
public class OrderConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 处理订单创建消息
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATE_QUEUE)
    public void handleOrderCreate(OrderMessageDTO message, Channel channel, Message msg) {
        log.info("收到订单创建消息: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
        
        try {
            Order order = orderMapper.selectById(message.getOrderId());
            if (order == null) {
                log.warn("订单不存在: orderId={}", message.getOrderId());
                ackMessage(channel, msg);
                return;
            }

            if (order.getStatus() != 0) {
                log.info("订单状态非待支付，跳过处理: orderId={}, status={}", 
                        message.getOrderId(), order.getStatus());
                ackMessage(channel, msg);
                return;
            }

            log.info("订单创建消息处理完成: orderId={}", message.getOrderId());
            ackMessage(channel, msg);
        } catch (Exception e) {
            handleConsumerError(message.getOrderId(), e, channel, msg);
        }
    }

    /**
     * 处理订单取消消息
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderCancel(OrderMessageDTO message, Channel channel, Message msg) {
        log.info("收到订单取消消息: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
        
        try {
            Order order = orderMapper.selectById(message.getOrderId());
            if (order == null) {
                log.warn("订单不存在: orderId={}", message.getOrderId());
                ackMessage(channel, msg);
                return;
            }

            if (order.getStatus() == 0) {
                order.setStatus(2);
                orderMapper.updateById(order);
                log.info("订单取消成功: orderId={}", message.getOrderId());
            }
            
            ackMessage(channel, msg);
            
        } catch (Exception e) {
            handleConsumerError(message.getOrderId(), e, channel, msg);
        }
    }

    /**
     * 处理订单超时消息（死信队列）
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_LETTER_QUEUE)
    public void handleOrderTimeout(OrderMessageDTO message, Channel channel, Message msg) {
        log.info("收到订单超时消息: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
        
        try {
            Order order = orderMapper.selectById(message.getOrderId());
            if (order == null) {
                log.warn("订单不存在: orderId={}", message.getOrderId());
                ackMessage(channel, msg);
                return;
            }

            if (order.getStatus() == 0) {
                log.info("订单超时，开始取消: orderId={}", message.getOrderId());
                
                order.setStatus(2);
                orderMapper.updateById(order);
                
                productMapper.incrementStock(order.getGoodsId());
                log.info("订单超时处理完成，已恢复库存: orderId={}, goodsId={}", 
                        message.getOrderId(), order.getGoodsId());
            } else {
                log.info("订单状态已变更，无需处理: orderId={}, status={}", 
                        message.getOrderId(), order.getStatus());
            }
            
            ackMessage(channel, msg);
            
        } catch (Exception e) {
            handleConsumerError(message.getOrderId(), e, channel, msg);
        }
    }

    /**
     * 统一处理消费者异常
     */
    private void handleConsumerError(Long orderId, Exception e, Channel channel, Message msg) {
        log.error("处理消息失败: orderId={}, error={}", orderId, e.getMessage());
        
        boolean requeued = !msg.getMessageProperties().getRedelivered();
        if (!requeued) {
            log.warn("消息已重试，拒绝并丢弃: orderId={}", orderId);
        }
        rejectMessage(channel, msg, requeued);
    }

    /**
     * 确认消息
     */
    private void ackMessage(Channel channel, Message msg) {
        try {
            channel.basicAck(msg.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            log.error("消息确认失败: {}", e.getMessage());
        }
    }

    /**
     * 拒绝消息
     * @param requeue 是否重新入队
     */
    private void rejectMessage(Channel channel, Message msg, boolean requeue) {
        try {
            channel.basicReject(msg.getMessageProperties().getDeliveryTag(), requeue);
        } catch (IOException e) {
            log.error("消息拒绝失败: {}", e.getMessage());
        }
    }
}