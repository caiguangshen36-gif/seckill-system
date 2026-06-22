package com.mvp.module.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mvp.common.annotation.RateLimit;
import com.mvp.common.dto.PageRequest;
import com.mvp.common.utils.Result;
import com.mvp.module.order.dto.OrderDto;
import com.mvp.module.order.service.OrderService;
import com.mvp.module.order.vo.OrderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 秒杀下单接口
     * 限流配置：每秒最多100个请求（全局限流）
     */
    @RateLimit(rate = 1000, rateInterval = 1000, message = "秒杀人数过多，请稍后重试")
    @PostMapping
    public Result<OrderVo> createOrder(@RequestBody @Validated OrderDto dto) {
        OrderVo order = orderService.createOrder(dto);
        return Result.success(order);
    }
    
    @PostMapping("/list")
    public Result<IPage<OrderVo>> getUserOrders(@RequestBody PageRequest pageRequest) {
        IPage<OrderVo> page = orderService.getUserOrders(pageRequest);
        return Result.success(page);
    }
    
    @GetMapping("/{id}")
    public Result<OrderVo> getOrderById(@PathVariable Long id) {
        OrderVo order = orderService.getOrderById(id);
        return Result.success(order);
    }
    
    @GetMapping("/no/{orderNo}")
    public Result<OrderVo> getOrderByOrderNo(@PathVariable String orderNo) {
        OrderVo order = orderService.getOrderByOrderNo(orderNo);
        return Result.success(order);
    }
    
    @PostMapping("/cancel")
    public Result<String> cancelOrder(@RequestParam Long id) {
        orderService.cancelOrder(id);
        return Result.success("取消成功");
    }
}
