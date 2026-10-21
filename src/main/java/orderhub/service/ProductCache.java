package orderhub.service;

import java.time.Duration;
import java.util.Optional;
import orderhub.api.Views.ProductView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProductCache {
    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final boolean enabled;

    public ProductCache(StringRedisTemplate redis, JsonMapper json,
                        @Value("${orderhub.cache.enabled}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
    }

    private String key(long id) { return "orderhub:product:v1:" + id; }

    public Optional<ProductView> get(long id) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key(id));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, ProductView.class));
        } catch (RuntimeException ex) {
            log.warn("Product cache read failed; using database ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(ProductView product) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key(product.id()), json.writeValueAsString(product), Duration.ofSeconds(60));
        } catch (RuntimeException ex) {
            log.warn("Product cache write failed ({})", ex.getClass().getSimpleName());
        }
    }

    public void evictAfterCommit(long id) {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evict(id); }
            });
        } else evict(id);
    }

    private void evict(long id) {
        try { redis.delete(key(id)); }
        catch (RuntimeException ex) { log.warn("Product cache eviction failed; TTL will expire ({})", ex.getClass().getSimpleName()); }
    }
}
