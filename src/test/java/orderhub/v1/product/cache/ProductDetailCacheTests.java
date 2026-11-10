package orderhub.v1.product.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import orderhub.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductDetailCacheTests {
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final JsonMapper json=JsonMapper.builder().build();
    private final ProductCacheProperties settings=new ProductCacheProperties(Duration.ofMinutes(30),Duration.ofMinutes(5),
        Duration.ofSeconds(60),Duration.ofSeconds(15),Duration.ofSeconds(10),Duration.ofMillis(30),Duration.ofMillis(5));
    private ProductDetailCache cache(){when(redis.opsForValue()).thenReturn(values);return new ProductDetailCache(redis,json,settings,true);}

    @Test void negativeHitNeverExecutesLoader(){
        var cache=cache();when(values.get("product:1")).thenReturn(ProductDetailCache.NOT_FOUND);
        assertThat(cache.get(1,String.class,()->{throw new AssertionError("Must not load");})).isNull();
        verify(values,never()).setIfAbsent(anyString(),anyString(),any(Duration.class));
    }
    @Test void disabledCacheNeverContactsRedis(){
        var cache=new ProductDetailCache(redis,json,settings,false);
        assertThat(cache.get(1,String.class,()->"value")).isEqualTo("value");verifyNoInteractions(redis);
    }
    @Test void redisFailureFallsBackOnceButDatabaseFailureIsNotRetried(){
        var cache=cache();when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        AtomicInteger calls=new AtomicInteger();
        assertThat(cache.get(1,String.class,()->{calls.incrementAndGet();return "database";})).isEqualTo("database");
        assertThat(calls).hasValue(1);
        doReturn(null).when(values).get(anyString());when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(true);
        assertThatThrownBy(()->cache.get(2,String.class,()->{calls.incrementAndGet();throw new IllegalStateException("DB down");})).isInstanceOf(IllegalStateException.class).hasMessage("DB down");
        assertThat(calls).hasValue(2);
    }
    @Test void boundedWaitNeverFallsThroughToDatabase(){
        var cache=cache();when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(false);
        assertThatThrownBy(()->cache.get(1,String.class,()->{throw new AssertionError("Must not bypass lock");}))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status.value()).isEqualTo(503));
    }
    @Test void ttlJitterStaysWithinConfiguredBounds(){
        var cache=cache();java.util.Set<Long> observed=new java.util.HashSet<>();
        for(int i=0;i<100;i++){
            long hit=cache.randomizedTtl(false),missing=cache.randomizedTtl(true);observed.add(hit);
            assertThat(hit).isBetween(1800000L,2100000L);assertThat(missing).isBetween(60000L,75000L);
        }
        assertThat(observed.size()).isGreaterThan(1);
    }
}
