package com.mvp.common.mq.consumer;

import com.mvp.common.mq.dto.StockMessageDTO;
import com.mvp.common.config.RabbitMQConfig;
import com.mvp.module.product.mapper.ProductMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 库存消息消费者
 */
@Slf4j
@Component
public class StockConsumer {

    @Autowired
    private ProductMapper productMapper;

    /**
     * 处理库存更新消息
     */
    @RabbitListener(queues = RabbitMQConfig.STOCK_UPDATE_QUEUE)
    public void handleStockUpdate(StockMessageDTO message, Channel channel, Message msg) {
        log.info("收到库存更新消息: goodsId={}, operationType={}, changeAmount={}", 
                message.getGoodsId(), message.getOperationType(), message.getChangeAmount());
        
        try {
            if ("DECREMENT".equals(message.getOperationType())) {
                int result = productMapper.decreaseStock(message.getGoodsId());
                if (result > 0) {
                    log.info("库存扣减成功: goodsId={}", message.getGoodsId());
                } else {
                    log.warn("库存扣减失败，库存不足: goodsId={}", message.getGoodsId());
                }
            } else if ("INCREMENT".equals(message.getOperationType())) {
                int result = productMapper.incrementStock(message.getGoodsId());
                if (result > 0) {
                    log.info("库存恢复成功: goodsId={}", message.getGoodsId());
                } else {
                    log.warn("库存恢复失败: goodsId={}", message.getGoodsId());
                }
            } else {
                log.warn("未知的库存操作类型: {}", message.getOperationType());
            }
            
            ackMessage(channel, msg);
            
        } catch (Exception e) {
            handleConsumerError(message.getGoodsId(), e, channel, msg);
        }
    }

    /**
     * 统一处理消费者异常
     */
    private void handleConsumerError(Long goodsId, Exception e, Channel channel, Message msg) {
        log.error("处理库存消息失败: goodsId={}, error={}", goodsId, e.getMessage());
        
        boolean requeued = !msg.getMessageProperties().getRedelivered();
        if (!requeued) {
            log.warn("消息已重试，拒绝并丢弃: goodsId={}", goodsId);
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