package com.mvp.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mvp.module.product.entity.Product;
import com.mvp.module.product.mapper.ProductMapper;
import com.mvp.module.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [Q7优化] 缓存预热Runner —— 应用启动后自动加载活跃秒杀商品到Redis
 *
 * 实现 ApplicationRunner 接口（而非 @PostConstruct）的好处：
 *   ApplicationRunner 在 Spring 容器完全初始化后执行（包括所有 Bean 注入、DataSource 就绪），
 *   此时 DB 连接池、Redis 连接池、MyBatis-Plus 配置等全部就位，不会出现 NPE 或连接失败。
 *   @PostConstruct 执行时机更早，可能在 DataSource 初始化前就触发——此时查 DB 会失败。
 *
 * 为什么只预热商品信息（不预热库存）？
 *   库存是强一致性数据，预热时从DB快照的值可能在预热完成瞬间就过时了。
 *   例如：
 *     T0：预热加载 商品A 库存=100 → Redis stock:100
 *     T1：运营/定时任务 修改 商品A 库存=50 → DB stock:50，但Redis仍是100
 *   如果预热库存，T0~T1窗口期的超卖风险需要额外机制兜底。
 *   因此库存走 lazy load（首次 getStock() 才加载），确保拿到的是最新值。
 *
 * TODO：多机部署时，每台实例都会独立预热打DB，建议加分布式锁或只在主节点执行
 */
@Slf4j
@Component
public class CacheWarmupRunner implements ApplicationRunner {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductService productService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("══════════════════════════════════════════");
        log.info("[缓存预热] 开始加载秒杀商品到Redis缓存...");
        log.info("══════════════════════════════════════════");

        // 查询所有"未开始"(status=0) 和 "进行中"(status=1) 的商品
        // 不预热已结束(status=2)和已下架(status=3)的——它们不会被访问
        List<Product> activeProducts = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getStatus, List.of(0, 1))
                        .orderByAsc(Product::getId)
        );

        if (activeProducts.isEmpty()) {
            log.info("[缓存预热] 无活跃秒杀商品，跳过预热");
            return;
        }

        int successCount = 0;
        for (Product p : activeProducts) {
            try {
                // 复用 ProductService.getProductById()：它内部实现了 Cache-Aside 模式
                // Redis有 → 直接返回 / Redis无 → 查DB → 回写Redis
                // 首次调用必然走"查DB→回写Redis"分支，正好完成预热
                productService.getProductById(p.getId());
                successCount++;
            } catch (Exception e) {
                log.error("[缓存预热] 商品ID={} 预热失败：{}", p.getId(), e.getMessage());
            }
        }

        log.info("══════════════════════════════════════════");
        log.info("[缓存预热] 完成！共 {} 个活跃商品，成功预热 {} 个",
                activeProducts.size(), successCount);
        log.info("[缓存预热] 注意：库存缓存采用lazy load，首次 getStock() 时才加载");
        log.info("══════════════════════════════════════════");
    }
}
