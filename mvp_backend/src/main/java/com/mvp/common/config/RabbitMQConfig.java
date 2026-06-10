package com.mvp.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 定义队列、交换机、绑定关系
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 队列名称 ====================
    public static final String ORDER_CREATE_QUEUE = "order.create.queue";
    public static final String STOCK_UPDATE_QUEUE = "stock.update.queue";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_DEAD_LETTER_QUEUE = "order.dead.letter.queue";

    // ==================== 交换机名称 ====================

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String STOCK_EXCHANGE = "stock.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    // ==================== 路由键 ====================
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";
    public static final String STOCK_UPDATE_ROUTING_KEY = "stock.update";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";
    public static final String ORDER_DEAD_LETTER_ROUTING_KEY = "order.dead.letter";

    // ==================== 创建队列 ====================
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", 600000) // 10分钟超时
                .build();
    }

    /**
     * 库存更新队列
     */
    @Bean
    public Queue stockUpdateQueue() {
        return QueueBuilder.durable(STOCK_UPDATE_QUEUE).build();
    }

    /**
     * 订单取消队列
     */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    /**
     * 死信队列（处理超时未支付订单）
     */
    @Bean
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DEAD_LETTER_QUEUE).build();
    }

    // ==================== 创建交换机 ====================
    
    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange stockExchange() {
        return ExchangeBuilder.directExchange(STOCK_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // ==================== 绑定队列到交换机 ====================
    
    @Bean
    public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue).to(orderExchange).with(ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    public Binding stockUpdateBinding(Queue stockUpdateQueue, DirectExchange stockExchange) {
        return BindingBuilder.bind(stockUpdateQueue).to(stockExchange).with(STOCK_UPDATE_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCancelQueue).to(orderExchange).with(ORDER_CANCEL_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue orderDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderDeadLetterQueue).to(deadLetterExchange).with(ORDER_DEAD_LETTER_ROUTING_KEY);
    }

    // ==================== 消息转换器 ====================
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}