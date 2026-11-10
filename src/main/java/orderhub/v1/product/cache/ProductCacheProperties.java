package orderhub.v1.product.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orderhub.cache.product")
public record ProductCacheProperties(Duration ttl, Duration ttlJitter, Duration nullTtl,
        Duration nullTtlJitter, Duration lockTtl, Duration waitTimeout, Duration retryInterval) {
    public ProductCacheProperties {
        for (Duration value : new Duration[]{ttl, nullTtl, lockTtl, waitTimeout, retryInterval})
            if (value == null || value.toMillis() <= 0) throw new IllegalArgumentException("Cache durations must be positive");
        for (Duration value : new Duration[]{ttlJitter, nullTtlJitter})
            if (value == null || value.isNegative()) throw new IllegalArgumentException("TTL jitter cannot be negative");
    }
}
