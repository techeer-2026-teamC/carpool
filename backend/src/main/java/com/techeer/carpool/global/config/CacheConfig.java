package com.techeer.carpool.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private final MeterRegistry meters;

    public CacheConfig(MeterRegistry meters) {
        this.meters = meters;
    }

    // 게시글 캐시: 오늘~+48h 출발 게시글, TTL 5분
    public static final String UPCOMING_POSTS = "upcoming-posts";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        return createCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(factory));
    }

    RedisCacheManager createCacheManager(RedisCacheWriter writer) {
        // LocalDateTime 직렬화를 위해 JavaTimeModule을 등록한 별도 ObjectMapper 사용
        ObjectMapper cacheObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        new ObjectMapper().getPolymorphicTypeValidator(),
                        ObjectMapper.DefaultTyping.NON_FINAL
                );

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)))
                .disableCachingNullValues();

        RedisCacheConfiguration upcomingPosts = base
                .entryTtl(Duration.ofMinutes(5));

        // Failed invalidation can leave stale entries until their remaining five-minute TTL expires.
        // Cache contents never authorize membership or reserve seats; those decisions use the database.
        RedisCacheManager manager = new RedisCacheManager(writer, base, Map.of(UPCOMING_POSTS, upcomingPosts)) {
            @Override
            protected RedisCache createRedisCache(String name, RedisCacheConfiguration configuration) {
                return new RedisCache(name, writer, configuration == null ? base : configuration) {
                    @Override
                    public void put(Object key, Object value) {
                        try { super.put(key, value); }
                        catch (RuntimeException error) { errorHandler().handleCachePutError(error, this, key, value); }
                    }
                    @Override
                    public void evict(Object key) {
                        try { super.evict(key); }
                        catch (RuntimeException error) { errorHandler().handleCacheEvictError(error, this, key); }
                    }
                    @Override
                    public void clear() {
                        try { super.clear(); }
                        catch (RuntimeException error) { errorHandler().handleCacheClearError(error, this); }
                    }
                };
            }
        };
        // Guard the underlying mutation: afterCommit runs outside CacheInterceptor's error handler.
        manager.setTransactionAware(true);
        return manager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private void failed(String operation) {
                meters.counter("cache.failures", "operation", operation).increment();
            }
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) { failed("get"); }
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) { failed("put"); }
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) { failed("evict"); }
            public void handleCacheClearError(RuntimeException e, Cache cache) { failed("clear"); }
        };
    }
}
