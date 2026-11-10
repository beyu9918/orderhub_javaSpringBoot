package orderhub.v1.coupon.claim;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import orderhub.v1.common.JsonbHandler;

@Component
public class CouponClaimGate {
    public static final int RESERVED=0, DUPLICATE=1, SOLD_OUT=2, INACTIVE=3, REBUILD=4;
    public record Reservation(long coupon, long user, String generation, String token) {}
    private final StringRedisTemplate redis;
    private final Duration reservationTimeout;
    private static final DefaultRedisScript<Long> INIT=script("coupon-init.lua"),
        RESERVE=script("coupon-reserve.lua"), FINISH=script("coupon-finish.lua");
    private static final DefaultRedisScript<Long> INVALIDATE=new DefaultRedisScript<>("""
        redis.call('SET', KEYS[6], ARGV[1], 'PX', 691200000)
        for i = 1, 5 do redis.call('DEL', KEYS[i]) end
        return 1
        """,Long.class);
    public CouponClaimGate(StringRedisTemplate redis,@Value("${orderhub.coupon.reservation-timeout}") Duration timeout) {
        if(timeout.toSeconds()<30)throw new IllegalArgumentException("Reservation timeout must be at least 30 seconds");
        this.redis=redis;this.reservationTimeout=timeout;
    }
    private static DefaultRedisScript<Long> script(String file) {
        var script=new DefaultRedisScript<Long>();script.setLocation(new ClassPathResource("redis/"+file));script.setResultType(Long.class);return script;
    }
    public List<String> keys(long coupon) {
        String tag="{"+coupon+"}";
        return List.of("coupon:state:"+tag,"coupon:stock:"+tag,"user:coupon:"+tag,"coupon:pending:"+tag,"coupon:pending-expiry:"+tag,"coupon:epoch:"+tag);
    }
    public String generation(long coupon) {
        List<String> k=keys(coupon);
        if(!Boolean.TRUE.equals(redis.hasKey(k.get(1)))||!Boolean.TRUE.equals(redis.hasKey(k.get(2))))return null;
        Object value=redis.opsForHash().get(k.getFirst(),"generation");return value==null?null:value.toString();
    }
    public String epoch(long coupon) {
        String key=keys(coupon).get(5);
        redis.opsForValue().setIfAbsent(key,UUID.randomUUID().toString(),Duration.ofDays(8));
        return Objects.requireNonNull(redis.opsForValue().get(key));
    }
    public void initialize(CouponClaimStore.Snapshot snapshot,String epoch) {
        var c=snapshot.coupon();
        long ttl=Math.max(Duration.ofHours(1).toMillis(),Math.min(Duration.ofDays(7).toMillis(),
            c.expireAt.toInstant().toEpochMilli()-System.currentTimeMillis()+Duration.ofDays(1).toMillis()));
        // User IDs are encoded as strings: Lua/cjson numbers cannot exactly represent all BIGINTs.
        redis.execute(INIT,keys(c.id),UUID.randomUUID().toString(),Integer.toString(c.totalQuantity-c.claimedQuantity),
            "ACTIVE".equals(c.status)?"1":"0",Long.toString(c.startAt.toInstant().toEpochMilli()),
            Long.toString(c.expireAt.toInstant().toEpochMilli()),Long.toString(ttl),
            JsonbHandler.encode(snapshot.users().stream().map(Object::toString).toList()),epoch);
    }
    public int reserve(Reservation r) {
        Long result=redis.execute(RESERVE,keys(r.coupon),r.generation,Long.toString(r.user),r.token,Long.toString(reservationTimeout.toMillis()));
        if(result==null)throw new IllegalStateException("Missing coupon script result");return result.intValue();
    }
    public boolean finish(Reservation r,boolean committed) {
        Long result=redis.execute(FINISH,keys(r.coupon),r.generation,Long.toString(r.user),r.token,committed?"1":"0");
        if(result==null)throw new IllegalStateException("Missing coupon completion result");return result>=0;
    }
    public Reservation pending(long coupon,long user) {
        List<String> k=keys(coupon);Object token=redis.opsForHash().get(k.get(3),Long.toString(user));String generation=generation(coupon);
        return token==null||generation==null?null:new Reservation(coupon,user,generation,token.toString());
    }
    public void invalidate(long coupon) { redis.execute(INVALIDATE,keys(coupon),UUID.randomUUID().toString()); }
    public List<long[]> expired(int limit) {
        List<long[]> result=new ArrayList<>();
        try(var cursor=redis.scan(ScanOptions.scanOptions().match("coupon:pending-expiry:*").count(100).build())) {
            while(cursor.hasNext()&&result.size()<limit) {
                String key=cursor.next();String id=key.substring(key.lastIndexOf('{')+1,key.lastIndexOf('}'));
                Set<String> users=redis.opsForZSet().rangeByScore(key,0,System.currentTimeMillis(),0,limit-result.size());
                if(users!=null)for(String user:users)result.add(new long[]{Long.parseLong(id),Long.parseLong(user)});
            }
        }
        return result;
    }
}
