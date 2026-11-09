package orderhub.v1.common;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommerceCache {
    private static final Logger LOG=LoggerFactory.getLogger(CommerceCache.class);
    private final StringRedisTemplate redis; private final JsonMapper json; private final boolean enabled;
    public CommerceCache(StringRedisTemplate redis,JsonMapper json,@Value("${orderhub.cache.enabled}") boolean enabled) {
        this.redis=redis;this.json=json;this.enabled=enabled;
    }
    public String get(String key) {
        if(!enabled) return null;
        try{return redis.opsForValue().get(key);}catch(RuntimeException e){LOG.debug("Cache read unavailable: {}",e.getClass().getSimpleName());return null;}
    }
    public void put(String key,String value,Duration ttl) {
        if(!enabled)return;
        try{redis.opsForValue().set(key,value,ttl);}catch(RuntimeException e){LOG.debug("Cache write unavailable: {}",e.getClass().getSimpleName());}
    }
    public <T> T remember(String key,Class<T> type,Duration ttl,Supplier<T> load) {
        String value=get(key);
        if(value!=null)try{return json.readValue(value,type);}catch(RuntimeException ignored){}
        T result=load.get();put(key,json.writeValueAsString(result),ttl);return result;
    }
    public void afterCommit(Runnable work) {
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){work.run();}});
        else work.run();
    }
    public void evict(String...keys) {
        afterCommit(()->{if(enabled)try{redis.delete(Arrays.asList(keys));}catch(RuntimeException e){LOG.debug("Cache eviction deferred to TTL");}});
    }
    public void viewed(long productId) {
        if(enabled)try{redis.opsForZSet().incrementScore("product:hot",Long.toString(productId),1);}catch(RuntimeException ignored){}
    }
}
