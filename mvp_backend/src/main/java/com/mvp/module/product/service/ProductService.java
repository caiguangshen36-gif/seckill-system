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
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    private static final long ACTIVE_LIST_CACHE_TTL = 10;
    private static final String ACTIVE_LIST_CACHE_KEY = "product:active:list";

    @Autowired
    private RedissonCacheService cacheService;

    /**
     * 应用启动时自动将 DB 库存同步到 Redis
     */
    @PostConstruct
    public void warmupStock() {
        log.info("开始Redis库存预热...");
        List<Product> products = productMapper.selectList(null);
        int count = 0;
        for (Product p : products) {
            if (p.getStatus() != null && p.getStatus() != 3) {
                cacheService.setProductStock(p.getId(), p.getStockCount());
                count++;
            }
        }
        log.info("Redis库存预热完成，共预热 {} 个商品", count);
    }

    /**
     * 应用启动时预热首页活动列表缓存，消除首次请求的 cache miss
     */
    @PostConstruct
    public void warmupActiveListCache() {
        log.info("开始预热活动列表缓存...");
        int[] pageSizes = {5, 10, 20, 50};
        for (int size : pageSizes) {
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(size);
            listActiveProducts(pageRequest);
        }
        log.info("活动列表缓存预热完成，已预热 {} 种分页规格", pageSizes.length);
    }

    public void addProduct(ProductDto dto) {
        if (dto.getStartTime() >= dto.getEndTime()) {
            throw new BusinessException("开始时间必须早于结束时间");
        }

        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        productMapper.insert(product);

        cacheService.setProductStock(product.getId(), product.getStockCount());
        cacheService.invalidateProductCache(product.getId());
        invalidateActiveListCache();

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
        invalidateActiveListCache();

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
        invalidateActiveListCache();
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
        String cacheKey = ACTIVE_LIST_CACHE_KEY + ":" + pageRequest.getPageNum() + ":" + pageRequest.getPageSize();

        Object cached = cacheService.get(cacheKey);
        if (cached instanceof ActiveListCacheEntry entry) {
            log.debug("从缓存获取进行中商品列表: page={}, size={}", pageRequest.getPageNum(), pageRequest.getPageSize());
            Page<ProductVo> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
            page.setRecords(entry.getRecords());
            page.setTotal(entry.getTotal());
            return page;
        }

        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(Product::getStartTime, now)
               .ge(Product::getEndTime, now)
               .ge(Product::getStockCount, 1)
               .orderByDesc(Product::getId);
        Page<Product> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<ProductVo> result = productMapper.selectPage(page, wrapper).convert(this::convertToVo);

        ActiveListCacheEntry entry = new ActiveListCacheEntry(result.getRecords(), result.getTotal());
        cacheService.set(cacheKey, entry, ACTIVE_LIST_CACHE_TTL);
        return result;
    }

    private void invalidateActiveListCache() {
        cacheService.delete(ACTIVE_LIST_CACHE_KEY + ":1:5");
        cacheService.delete(ACTIVE_LIST_CACHE_KEY + ":1:10");
        cacheService.delete(ACTIVE_LIST_CACHE_KEY + ":1:20");
        cacheService.delete(ACTIVE_LIST_CACHE_KEY + ":1:50");
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
        Integer cachedStock = cacheService.getProductStock(productId);

        if (cachedStock != null) {
            int luaResult = cacheService.checkAndDecrementStock(productId);
            if (luaResult != 1) {
                String reason = switch (luaResult) {
                    case 0 -> "库存不足";
                    case -1 -> "Redis key不存在";
                    case -2 -> "库存校验冲突";
                    default -> "未知错误";
                };
                log.warn("[Lua扣减失败] productId={}, result={}, reason={}", productId, luaResult, reason);
                return false;
            }
        }

        int dbResult = productMapper.decreaseStock(productId);
        if (dbResult <= 0) {
            if (cachedStock != null) {
                cacheService.incrementStock(productId);
                log.warn("[DB扣减失败-回滚Redis] productId={}", productId);
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

    /**
     * 活动列表缓存条目（替代无法序列化的 MyBatis-Plus IPage）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveListCacheEntry implements Serializable {
        private List<ProductVo> records;
        private long total;
    }
}