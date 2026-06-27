package com.mvp.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mvp.common.dto.PageRequest;
import com.mvp.common.exption.BusinessException;
import com.mvp.common.utils.RedissonCacheService;
import com.mvp.module.product.dto.ProductDto;
import com.mvp.module.product.dto.ProductQueryRequest;
import com.mvp.module.product.entity.Product;
import com.mvp.module.product.mapper.ProductMapper;
import com.mvp.module.product.vo.ProductVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedissonCacheService cacheService;

    public void addProduct(ProductDto dto) {
        if (dto.getStartTime() >= dto.getEndTime()) {
            throw new BusinessException("开始时间必须早于结束时间");
        }

        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        productMapper.insert(product);

        cacheService.setProductStock(product.getId(), product.getStockCount());
        cacheService.invalidateProductCache(product.getId());

        log.info("添加秒杀商品成功：{}", dto.getGoodsName());
    }

    public void updateProduct(Long id, ProductDto dto) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        if (dto.getStartTime() >= dto.getEndTime()) {
            throw new BusinessException("开始时间必须早于结束时间");
        }

        product.setGoodsName(dto.getGoodsName());
        product.setSeckillPrice(dto.getSeckillPrice());
        product.setStockCount(dto.getStockCount());
        product.setStartTime(dto.getStartTime());
        product.setEndTime(dto.getEndTime());
        productMapper.updateById(product);

        cacheService.setProductStock(id, dto.getStockCount());
        cacheService.invalidateProductCache(id);

        log.info("更新秒杀商品成功：{}", id);
    }

    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        productMapper.deleteById(id);

        cacheService.deleteProduct(id);
        cacheService.deleteProductStock(id);
        log.info("删除秒杀商品成功：{}", id);
    }

    public ProductVo getProductById(Long id) {
        ProductVo cachedProduct = (ProductVo) cacheService.getProduct(id);
        if (cachedProduct != null) {
            log.debug("从缓存获取商品: id={}", id);
            return cachedProduct;
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        ProductVo vo = convertToVo(product);
        cacheService.setProduct(id, vo);

        return vo;
    }

    public IPage<ProductVo> listProducts(PageRequest pageRequest) {
        Page<Product> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Product::getId);
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);
        return productPage.convert(this::convertToVo);
    }

    public IPage<ProductVo> listActiveProducts(PageRequest pageRequest) {
        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(Product::getStartTime, now)
               .ge(Product::getEndTime, now)
               .ge(Product::getStockCount, 1)
               .orderByDesc(Product::getId);
        Page<Product> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);
        return productPage.convert(this::convertToVo);
    }

    public IPage<ProductVo> queryProducts(ProductQueryRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getGoodsName())) {
            wrapper.like(Product::getGoodsName, request.getGoodsName());
        }
        if (request.getMinPrice() != null) {
            wrapper.ge(Product::getSeckillPrice, request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            wrapper.le(Product::getSeckillPrice, request.getMaxPrice());
        }

        wrapper.orderByDesc(Product::getId);

        Page<Product> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);
        return productPage.convert(this::convertToVo);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseStock(Long productId) {
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

    public Integer getStock(Long productId) {
        Integer cachedStock = (Integer) cacheService.getProductStock(productId);
        if (cachedStock != null) {
            log.debug("从缓存获取库存: productId={}, stock={}", productId, cachedStock);
            return cachedStock;
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        cacheService.setProductStock(productId, product.getStockCount());
        return product.getStockCount();
    }

    @Scheduled(fixedRate = 60000)
    public void updateExpiredProducts() {
        long now = System.currentTimeMillis() / 1000;
        
        // 查询即将过期的商品ID（用于清除库存缓存）
        List<Product> expiringProducts = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .lt(Product::getEndTime, now)
                        .ne(Product::getStatus, 3)
        );
        
        if (!expiringProducts.isEmpty()) {
            // 批量更新过期商品状态
            LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
            wrapper.lt(Product::getEndTime, now)
                   .ne(Product::getStatus, 3)
                   .set(Product::getStatus, 3);
            int updatedCount = productMapper.update(null, wrapper);

            // 清除过期商品的库存缓存
            expiringProducts.forEach(p -> cacheService.deleteProductStock(p.getId()));
            log.info("批量更新过期商品数量：{}，已清除库存缓存", updatedCount);
        }
    }

    public Integer getProductStatus(Product product) {
        long now = System.currentTimeMillis() / 1000;
        if (now < product.getStartTime()) {
            return 0;
        } else if (now >= product.getStartTime() && now <= product.getEndTime()) {
            return 1;
        } else {
            return 3;
        }
    }

    private ProductVo convertToVo(Product product) {
        ProductVo vo = new ProductVo();
        BeanUtils.copyProperties(product, vo);

        int currentStatus = getProductStatus(product);
        vo.setStatus(currentStatus);
        vo.setStatusDesc(getStatusDesc(currentStatus));

        return vo;
    }

    private String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "进行中";
            case 2 -> "已结束";
            case 3 -> "已下架";
            default -> "未知";
        };
    }
}