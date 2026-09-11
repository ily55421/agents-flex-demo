package com.agentsflex.showcase.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.util.concurrent.TimeUnit;

/**
 * Ehcache 3 缓存配置。
 *
 * <p>通过 JSR-107 (JCache) 接入 Ehcache，并把 {@code javax.cache.CacheManager} 适配为
 * Spring {@link CacheManager}，使 {@code @Cacheable}/{@code @CacheEvict} 注解直接生效。
 * 三个缓存按数据特征区分 TTL：Agent 定义不可变使用较长 TTL；模型状态在创建 Agent 时显式失效；
 * 归档 Run 快照在写入时逐条失效，保证活跃 Run 不被缓存掩盖。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String EHCACHE_PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";

    /**
     * 创建 Ehcache 的 JCache CacheManager，并预注册本项目使用的全部缓存。
     * 预创建避免运行时按默认配置动态建缓存带来的类型不确定问题。
     *
     * @return 生命周期由 Spring 管理的 JCache CacheManager
     */
    @Bean(destroyMethod = "close")
    public javax.cache.CacheManager ehcacheCacheManager() {
        CachingProvider provider = Caching.getCachingProvider(EHCACHE_PROVIDER);
        javax.cache.CacheManager manager = provider.getCacheManager();
        createCache(manager, "agentDefinitions", 10, TimeUnit.MINUTES);
        createCache(manager, "modelStatus", 1, TimeUnit.MINUTES);
        createCache(manager, "archivedRunSnapshots", 10, TimeUnit.MINUTES);
        return manager;
    }

    /**
     * 将 JCache CacheManager 适配为 Spring 缓存抽象；null 值不进缓存，
     * 避免 JCache 对 null value 的约束破坏不存在数据的查询路径。
     *
     * @param ehcacheCacheManager 上面创建的 JCache 管理器
     * @return Spring CacheManager 适配器
     */
    @Bean
    public CacheManager cacheManager(javax.cache.CacheManager ehcacheCacheManager) {
        JCacheCacheManager manager = new JCacheCacheManager(ehcacheCacheManager);
        manager.setAllowNullValues(false);
        return manager;
    }

    /**
     * 按名称创建字符串键缓存；值使用 Ehcache 默认的 Java 序列化（storeByValue），
     * 保证缓存内容与调用方解耦。
     *
     * @param manager    目标 JCache 管理器
     * @param name       缓存名
     * @param ttl        生存时间数值
     * @param unit       生存时间单位
     */
    private static void createCache(javax.cache.CacheManager manager, String name,
                                    long ttl, TimeUnit unit) {
        if (manager.getCache(name) != null) return;
        MutableConfiguration<String, Object> configuration = new MutableConfiguration<>();
        configuration.setTypes(String.class, Object.class);
        configuration.setStoreByValue(true);
        configuration.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(unit, ttl)));
        configuration.setStatisticsEnabled(true);
        manager.createCache(name, configuration);
    }
}
