package orderhub;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import orderhub.api.Views.ProductView;
import orderhub.service.ProductCache;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCacheTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void storesTypedJsonWithTtlAndReadsItBack() {
        when(redis.opsForValue()).thenReturn(values);
        var cache = new ProductCache(redis, json, true);
        var product = new ProductView(1, "sku", "Keyboard", "desc", "electronics",
                new BigDecimal("49.90"), true, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        cache.put(product);
        verify(values).set("orderhub:product:v1:1", json.writeValueAsString(product), Duration.ofSeconds(60));
        when(values.get("orderhub:product:v1:1")).thenReturn(json.writeValueAsString(product));
        assertThat(cache.get(1)).contains(product);
    }

    @Test
    void connectionFailureAndCorruptedJsonBecomeCacheMisses() {
        when(redis.opsForValue()).thenReturn(values);
        var cache = new ProductCache(redis, json, true);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        assertThat(cache.get(1)).isEmpty();
        doReturn("invalid-json").when(values).get(anyString());
        assertThat(cache.get(1)).isEmpty();
        doThrow(new RedisConnectionFailureException("offline")).when(redis).delete(anyString());
        assertThatCode(() -> cache.evictAfterCommit(1)).doesNotThrowAnyException();
    }

    @Test
    void evictsOnlyAfterSuccessfulCommit() {
        var cache = new ProductCache(redis, json, true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(1);
            verifyNoInteractions(redis);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redis).delete("orderhub:product:v1:1");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        reset(redis);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(redis);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
