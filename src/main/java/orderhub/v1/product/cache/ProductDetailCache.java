package orderhub.v1.product.cache;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import orderhub.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Null caching and a bounded cross-instance single-flight for public product details. */
@Component
@EnableConfigurationProperties(ProductCacheProperties.class)
public class ProductDetailCache {
    public static final String NOT_FOUND = "~orderhub:product:not-found:v1~";
    private static final Logger LOG = LoggerFactory.getLogger(ProductDetailCache.class);
    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final ProductCacheProperties settings;
    private final boolean enabled;

    public ProductDetailCache(StringRedisTemplate redis, JsonMapper json, ProductCacheProperties settings,
            @Value("${orderhub.cache.enabled}") boolean enabled) {
        this.redis = redis; this.json = json; this.settings = settings; this.enabled = enabled;
    }

    public <T> T get(long productId, Class<T> type, Supplier<T> loader) {
        if (!enabled) return loader.get();
        String key = "product:" + productId, lockKey = "product:lock:" + productId;
        long deadline = System.nanoTime() + settings.waitTimeout().toNanos();
        try {
            while (true) {
                Cached<T> cached = read(key, type);
                if (cached.hit()) return cached.value();
                String token = UUID.randomUUID().toString();
                if (acquire(lockKey, token)) {
                    try {
                        // A previous owner may have populated the value immediately before our lock.
                        cached = read(key, type);
                        if (cached.hit()) return cached.value();
                        T result = loader.get();
                        long ttl = randomizedTtl(result == null);
                        publish(lockKey, key, token, result == null ? NOT_FOUND : json.writeValueAsString(result), ttl);
                        return result;
                    } finally { unlock(lockKey, token); }
                }
                if (System.nanoTime() >= deadline)
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "商品缓存正在重建，请稍后重试");
                try {
                    Thread.sleep(settings.retryInterval().toMillis() + ThreadLocalRandom.current().nextLong(11));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "商品查询已中断，请重试");
                }
            }
        } catch (RedisUnavailable ex) {
            LOG.debug("Product cache unavailable; loading database: {}", ex.getCause().getClass().getSimpleName());
            return loader.get();
        }
    }

    private <T> Cached<T> read(String key, Class<T> type) {
        String raw;
        try { raw = redis.opsForValue().get(key); } catch (RuntimeException e) { throw new RedisUnavailable(e); }
        if (raw == null) return new Cached<>(false, null);
        if (NOT_FOUND.equals(raw)) return new Cached<>(true, null);
        try { return new Cached<>(true, json.readValue(raw, type)); }
        catch (RuntimeException e) { return new Cached<>(false, null); }
    }
    private boolean acquire(String key, String token) {
        try { return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, settings.lockTtl())); }
        catch (RuntimeException e) { throw new RedisUnavailable(e); }
    }
    private void publish(String lockKey, String key, String token, String value, long ttl) {
        // An expired owner cannot overwrite a successor's value or release its lock.
        // Publishing failure must not repeat a successful database load.
        try { redis.execute(PUBLISH, List.of(lockKey, key), token, value, Long.toString(ttl)); }
        catch (RuntimeException e) { LOG.debug("Product cache publication failed: {}", e.getClass().getSimpleName()); }
    }
    private void unlock(String key, String token) {
        try { redis.execute(UNLOCK, List.of(key), token); }
        catch (RuntimeException e) { LOG.debug("Product rebuild lock will expire"); }
    }
    long randomizedTtl(boolean missing) {
        Duration base = missing ? settings.nullTtl() : settings.ttl();
        Duration jitter = missing ? settings.nullTtlJitter() : settings.ttlJitter();
        return base.toMillis() + ThreadLocalRandom.current().nextLong(jitter.toMillis() + 1);
    }
    private record Cached<T>(boolean hit, T value) {}
    private static final class RedisUnavailable extends RuntimeException {
        RedisUnavailable(RuntimeException cause) { super(cause); }
    }
}
