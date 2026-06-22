package com.mvp.module.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mvp.common.annotation.RateLimit;
import com.mvp.common.dto.PageRequest;
import com.mvp.common.utils.Result;
import com.mvp.module.product.dto.ProductDto;
import com.mvp.module.product.dto.ProductQueryRequest;
import com.mvp.module.product.service.ProductService;
import com.mvp.module.product.vo.ProductVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/product")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    /**
     * 添加秒杀商品
     */
    @RateLimit(rate = 10, rateInterval = 1000, message = "操作过于频繁")
    @PostMapping("/add")
    public Result<String> addProduct(@RequestBody @Validated ProductDto dto) {
        productService.addProduct(dto);
        return Result.success("添加成功");
    }
    
    /**
     * 更新秒杀商品
     */
    @RateLimit(rate = 10, rateInterval = 1000, message = "操作过于频繁")
    @PostMapping("/update")
    public Result<String> updateProduct(@RequestBody @Validated ProductDto dto) {
        productService.updateProduct(dto.getId(), dto);
        return Result.success("更新成功");
    }
    
    /**
     * 删除秒杀商品
     */
    @RateLimit(rate = 10, rateInterval = 1000, message = "操作过于频繁")
    @PostMapping("/delete")
    public Result<String> deleteProduct(@RequestParam Long id) {
        productService.deleteProduct(id);
        return Result.success("删除成功");
    }
    
    /**
     * 查询单个商品（高频接口，限流500/秒）
     */
    @RateLimit(rate = 2000, rateInterval = 1000, name = "product_detail")
    @GetMapping("/detail")
    public Result<ProductVo> getProductById(@RequestParam Long id) {
        ProductVo product = productService.getProductById(id);
        return Result.success(product);
    }
    
    /**
     * 分页查询所有商品（高频接口，限流200/秒）
     */
    @RateLimit(rate = 200, rateInterval = 1000, name = "product_list")
    @PostMapping("/list")
    public Result<IPage<ProductVo>> listProducts(@RequestBody PageRequest pageRequest) {
        IPage<ProductVo> page = productService.listProducts(pageRequest);
        return Result.success(page);
    }
    
    /**
     * 分页查询进行中的商品（秒杀商品列表，限流300/秒）
     */
    @RateLimit(rate = 1000, rateInterval = 1000, name = "product_active")
    @PostMapping("/active")
    public Result<IPage<ProductVo>> listActiveProducts(@RequestBody PageRequest pageRequest) {
        IPage<ProductVo> page = productService.listActiveProducts(pageRequest);
        return Result.success(page);
    }
    
    /**
     * 获取商品库存
     */
    @RateLimit(rate = 500, rateInterval = 1000, name = "product_stock")
    @GetMapping("/stock")
    public Result<Integer> getStock(@RequestParam Long id) {
        Integer stock = productService.getStock(id);
        return Result.success(stock);
    }
    
    /**
     * 分页条件查询商品
     */
    @RateLimit(rate = 200, rateInterval = 1000, name = "product_query")
    @PostMapping("/query")
    public Result<IPage<ProductVo>> queryProducts(@RequestBody ProductQueryRequest request) {
        IPage<ProductVo> page = productService.queryProducts(request);
        return Result.success(page);
    }
}