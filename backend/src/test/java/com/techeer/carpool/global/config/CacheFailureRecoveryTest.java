package com.techeer.carpool.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheFailureRecoveryTest {
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisCacheWriter writer = mock(RedisCacheWriter.class);
    private final CacheConfig config = new CacheConfig(meters);
    private RedisCacheManager manager;
    private Cache cache;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        manager = config.createCacheManager(writer);
        manager.afterPropertiesSet();
        cache = manager.getCache(CacheConfig.UPCOMING_POSTS);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE decisions (id BIGINT PRIMARY KEY)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("SHUTDOWN");
        meters.close();
    }

    @Test
    void writesAndInvalidationWaitUntilDatabaseCommit() {
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO decisions VALUES (1)");
            mutateCache();
            verifyNoInteractions(writer);
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decisions", Integer.class)).isEqualTo(1);
        verify(writer).put(eq(CacheConfig.UPCOMING_POSTS), any(byte[].class), any(byte[].class), eq(Duration.ofMinutes(5)));
        verify(writer).evict(eq(CacheConfig.UPCOMING_POSTS), any(byte[].class));
        verify(writer).clean(eq(CacheConfig.UPCOMING_POSTS), any(byte[].class));
    }

    @Test
    void rollbackNeverPublishesCacheWritesOrInvalidation() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO decisions VALUES (1)");
            mutateCache();
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decisions", Integer.class)).isZero();
        verifyNoInteractions(writer);
    }

    @Test
    void redisFailureAfterCommitDoesNotTurnCommittedDecisionIntoFailure() {
        var unavailable = new RedisConnectionFailureException("offline");
        doThrow(unavailable).when(writer).put(anyString(), any(byte[].class), any(byte[].class), any(Duration.class));
        doThrow(unavailable).when(writer).evict(anyString(), any(byte[].class));
        doThrow(unavailable).when(writer).clean(anyString(), any(byte[].class));

        assertThatCode(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO decisions VALUES (1)");
            mutateCache();
        })).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decisions", Integer.class)).isEqualTo(1);
        for (String operation : new String[]{"put", "evict", "clear"}) {
            assertThat(meters.get("cache.failures").tag("operation", operation).counter().count()).isEqualTo(1);
        }
        assertThat(meters.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting("key").containsExactly("operation"));
    }

    @Test
    void cacheableReadFallsBackToLoaderWhenRedisCannotBeRead() {
        when(writer.get(anyString(), any(byte[].class))).thenThrow(new RedisConnectionFailureException("offline"));
        CacheInterceptor interceptor = new CacheInterceptor();
        interceptor.setCacheManager(manager);
        interceptor.setErrorHandler(config.errorHandler());
        interceptor.setCacheOperationSources(new AnnotationCacheOperationSource());
        interceptor.afterPropertiesSet();
        interceptor.afterSingletonsInstantiated();
        var factory = new ProxyFactory(new DatabaseRead());
        factory.addAdvice(interceptor);
        DatabaseRead service = (DatabaseRead) factory.getProxy();

        assertThat(service.read("page:1")).isEqualTo("database result");
        assertThat(meters.get("cache.failures").tag("operation", "get").counter().count()).isEqualTo(1);
    }

    private void mutateCache() {
        cache.put("page:1", "updated result");
        cache.evict("page:2");
        cache.clear();
    }

    static class DatabaseRead {
        @Cacheable(cacheNames = CacheConfig.UPCOMING_POSTS)
        public String read(String key) { return "database result"; }
    }
}
