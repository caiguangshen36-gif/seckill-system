package com.mvp.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mvp.common.dto.PageRequest;
import com.mvp.common.exption.BusinessException;
import com.mvp.common.mq.dto.OrderMessageDTO;
import com.mvp.common.mq.producer.MessageProducer;
import com.mvp.common.utils.RedissonCacheService;
import com.mvp.common.utils.ThreadLocalUtil;
import com.mvp.module.order.dto.OrderDto;
import com.mvp.module.order.entity.Order;
import com.mvp.module.order.mapper.OrderMapper;
import com.mvp.module.order.vo.OrderVo;
import com.mvp.module.product.entity.Product;
import com.mvp.module.product.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrderService {

    private static final long PAY_EXPIRE_SECONDS = 900;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedissonCacheService cacheService;

    @Autowired(required = false)
    private MessageProducer messageProducer;


    @Transactional(rollbackFor = Exception.class)
    public OrderVo createOrder(OrderDto dto) {

        Long userId = ThreadLocalUtil.getUserId();
        Long goodsId = dto.getGoodsId();
        long now = System.currentTimeMillis() / 1000;

        Product product = productMapper.selectById(goodsId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        if (now < product.getStartTime()) {
            throw new BusinessException("秒杀尚未开始");
        }
        if (now > product.getEndTime()) {
            throw new BusinessException("秒杀已结束");
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId).eq(Order::getGoodsId, goodsId);
        Order existOrder = orderMapper.selectOne(wrapper);
        if (existOrder != null) {
            throw new BusinessException("您已购买过该商品");
        }


        // 尝试扣减库存
        boolean stockDecreased = decreaseStockWithCache(goodsId);
        if (!stockDecreased) {
            throw new BusinessException("库存不足，下单失败");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setOrderNo(generateOrderNo());
        order.setOrderPrice(dto.getOrderPrice());
        order.setStatus(1);
        order.setCreateTime(now);
        order.setPayExpireTime(now + PAY_EXPIRE_SECONDS);
        orderMapper.insert(order);

        cacheService.deleteProduct(goodsId);
        cacheService.setOrder(order.getId(), convertToVo(order, product.getGoodsName()));

        // 发送订单创建消息（异步处理后续逻辑）
        if (messageProducer != null) {
            OrderMessageDTO orderMessage = OrderMessageDTO.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .userId(userId)
                    .goodsId(goodsId)
                    .goodsName(product.getGoodsName())
                    .orderPrice(order.getOrderPrice())
                    .status(order.getStatus())
                    .createTime(order.getCreateTime())
                    .messageType("CREATE")
                    .build();
            messageProducer.sendOrderCreateMessage(orderMessage);
        }

        log.info("========================================");
        log.info("[秒杀下单] 订单创建成功");
        log.info("[秒杀下单] 用户ID: {}, 商品ID: {}, 订单号: {}", userId, goodsId, order.getOrderNo());
        log.info("[秒杀下单] 订单金额: {}, 状态: {}", order.getOrderPrice(), order.getStatus() == 0 ? "待支付" : "已支付");
        log.info("[秒杀下单] 支付超时时间: {}秒", PAY_EXPIRE_SECONDS);
        log.info("========================================");

        return convertToVo(order, product.getGoodsName());
    }

    private boolean decreaseStockWithCache(Long productId) {
        Integer cachedStock = (Integer) cacheService.getProductStock(productId);
        if (cachedStock != null && cachedStock <= 0) {
            log.warn("缓存库存不足: productId={}", productId);
            return false;
        }

        if (cachedStock != null) {
            boolean success = cacheService.decrementStock(productId);
            if (!success) {
                return false;
            }
        }

        int result = productMapper.decreaseStock(productId);
        if (result <= 0) {
            if (cachedStock != null) {
                cacheService.incrementStock(productId);
            }
            return false;
        }

        return true;
    }

    public IPage<OrderVo> getUserOrders(PageRequest pageRequest) {
        Long userId = ThreadLocalUtil.getUserId();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId).orderByDesc(Order::getCreateTime);

        Page<Order> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<Order> orderPage = orderMapper.selectPage(page, wrapper);

        return orderPage.convert(order -> {
            Product product = productMapper.selectById(order.getGoodsId());
            String goodsName = product != null ? product.getGoodsName() : "商品已删除";
            return convertToVo(order, goodsName);
        });
    }

    public OrderVo getOrderById(Long orderId) {
        Long userId = ThreadLocalUtil.getUserId();

        OrderVo cachedOrder = (OrderVo) cacheService.getOrder(orderId);
        if (cachedOrder != null) {
            if (!cachedOrder.getUserId().equals(userId)) {
                throw new BusinessException("无权查看该订单");
            }
            return cachedOrder;
        }

        Order order = orderMapper.selectById(orderId);

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }

        Product product = productMapper.selectById(order.getGoodsId());
        String goodsName = product != null ? product.getGoodsName() : "商品已删除";
        OrderVo vo = convertToVo(order, goodsName);

        cacheService.setOrder(orderId, vo);

        return vo;
    }

    public OrderVo getOrderByOrderNo(String orderNo) {
        Long userId = ThreadLocalUtil.getUserId();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }

        Product product = productMapper.selectById(order.getGoodsId());
        String goodsName = product != null ? product.getGoodsName() : "商品已删除";
        return convertToVo(order, goodsName);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Long userId = ThreadLocalUtil.getUserId();
        Order order = orderMapper.selectById(orderId);

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }

        if (order.getStatus() != 0) {
            throw new BusinessException("只能取消待支付订单");
        }

        order.setStatus(2);
        orderMapper.updateById(order);

        cacheService.incrementStock(order.getGoodsId());
        cacheService.deleteOrder(orderId);
        cacheService.deleteProduct(order.getGoodsId());

        // 发送订单取消消息（异步处理后续逻辑）
        if (messageProducer != null) {
            OrderMessageDTO orderMessage = OrderMessageDTO.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .userId(userId)
                    .goodsId(order.getGoodsId())
                    .orderPrice(order.getOrderPrice())
                    .status(order.getStatus())
                    .createTime(order.getCreateTime())
                    .messageType("CANCEL")
                    .build();
            messageProducer.sendOrderCancelMessage(orderMessage);
        }

        log.info("========================================");
        log.info("[订单取消] 订单取消成功");
        log.info("[订单取消] 用户ID: {}, 订单ID: {}, 订单号: {}", userId, orderId, order.getOrderNo());
        log.info("[订单取消] 商品ID: {}, 订单金额: {}", order.getGoodsId(), order.getOrderPrice());
        log.info("[订单取消] 库存已恢复");
        log.info("========================================");
    }

    private OrderVo convertToVo(Order order, String goodsName) {
        OrderVo vo = new OrderVo();
        BeanUtils.copyProperties(order, vo);
        vo.setGoodsName(goodsName);
        return vo;
    }

    /**
     * 生成全局唯一订单号（MyBatis-Plus 雪花算法）
     * 示例：ORD202406081234567890
     */
    private String generateOrderNo() {
        return "ORD" + IdWorker.getIdStr();
    }
}