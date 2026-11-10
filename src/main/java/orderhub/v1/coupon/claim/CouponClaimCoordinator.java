package orderhub.v1.coupon.claim;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import orderhub.api.ApiException;
import orderhub.v1.common.Db;
import orderhub.v1.coupon.entity.UserCoupon;
import org.redisson.api.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CouponClaimCoordinator {
    private static final Logger LOG=LoggerFactory.getLogger(CouponClaimCoordinator.class);
    private final CouponClaimStore store;
    private final CouponClaimGate gate;
    private final ObjectProvider<RedissonClient> clients;
    private final boolean enabled;
    private final Duration wait;
    public CouponClaimCoordinator(CouponClaimStore store,CouponClaimGate gate,ObjectProvider<RedissonClient> clients,
            @Value("${orderhub.coupon.redis-enabled}") boolean enabled,@Value("${orderhub.coupon.lock-wait}") Duration wait) {
        this.store=store;this.gate=gate;this.clients=clients;this.enabled=enabled;this.wait=wait;
    }
    public static String lockKey(long coupon,long user) { return "coupon:claim:{"+coupon+"}:"+user; }

    public UserCoupon claim(long user,long coupon) {
        if(!enabled)return store.claim(user,coupon);
        RLock lock;
        try {
            lock=clients.getObject().getLock(lockKey(coupon,user));
            // No fixed lease: Redisson watchdog renews while the DB transaction is in progress.
            if(!lock.tryLock(wait.toMillis(),TimeUnit.MILLISECONDS))throw busy();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw busy();
        } catch(ApiException e) { throw e;
        } catch(RuntimeException e) { return databaseFallback(user,coupon,e); }
        try { return guardedClaim(user,coupon); }
        finally { unlock(lock); }
    }
    private UserCoupon guardedClaim(long user,long coupon) {
        CouponClaimGate.Reservation reservation=null;
        int code;
        try {
            for(int attempt=0;;attempt++) {
                String generation=ensureInitialized(coupon);
                reservation=new CouponClaimGate.Reservation(coupon,user,generation,UUID.randomUUID().toString());
                code=gate.reserve(reservation);
                if(code!=CouponClaimGate.REBUILD)break;
                if(attempt>=2)throw busy();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw busy();
        } catch(ApiException e) { throw e;
        } catch(RuntimeException e) { return databaseFallback(user,coupon,e); }

        if(code==CouponClaimGate.DUPLICATE) {
            UserCoupon existing=store.find(user,coupon);
            if(existing!=null) { finishPending(coupon,user,true);return existing; }
            // Previous process died after Lua. We hold its user lock, so recover and retry once.
            finishPending(coupon,user,false);
            return guardedClaimAfterRecovery(user,coupon);
        }
        Db.check(code!=CouponClaimGate.SOLD_OUT,"优惠券已领完");
        Db.check(code!=CouponClaimGate.INACTIVE,"优惠券不在可领取时段");
        Db.check(code==CouponClaimGate.RESERVED,"抢券状态异常，请重试");
        return persist(reservation);
    }
    private UserCoupon guardedClaimAfterRecovery(long user,long coupon) {
        try {
            String generation=ensureInitialized(coupon);
            var reservation=new CouponClaimGate.Reservation(coupon,user,generation,UUID.randomUUID().toString());
            int code=gate.reserve(reservation);
            Db.check(code==CouponClaimGate.RESERVED,"抢券正在恢复或库存不足，请重试");
            return persist(reservation);
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw busy(); }
    }
    private UserCoupon persist(CouponClaimGate.Reservation r) {
        UserCoupon result;
        try { result=store.claim(r.user(),r.coupon()); }
        catch(RuntimeException e) {
            // This compensates only our token. If Redis is down, the pending ledger survives for recovery.
            finish(r,false);
            // A stale gate (e.g. DB-only claims during an outage) must be rebuilt from PostgreSQL.
            invalidate(r.coupon());
            throw e;
        }
        finish(r,true);
        return result;
    }
    private String ensureInitialized(long coupon) throws InterruptedException {
        String generation=gate.generation(coupon);if(generation!=null)return generation;
        RLock init=clients.getObject().getLock("coupon:init:{"+coupon+"}");
        if(!init.tryLock(wait.toMillis(),TimeUnit.MILLISECONDS))throw busy();
        try {
            generation=gate.generation(coupon);
            if(generation==null){String epoch=gate.epoch(coupon);gate.initialize(store.snapshot(coupon),epoch);generation=gate.generation(coupon);}
            if(generation==null)throw busy();return generation;
        } finally { unlock(init); }
    }
    private UserCoupon databaseFallback(long user,long coupon,RuntimeException reason) {
        LOG.warn("Coupon {} using database fallback ({})",coupon,reason.getClass().getSimpleName());
        try { return store.claim(user,coupon); }
        finally { invalidate(coupon); }
    }
    public void invalidate(long coupon) {
        if(enabled)try{gate.invalidate(coupon);}catch(RuntimeException e){LOG.debug("Coupon state will be reconciled on recovery");}
    }
    private void finish(CouponClaimGate.Reservation r,boolean committed) {
        try { if(!gate.finish(r,committed))invalidate(r.coupon()); }
        catch(RuntimeException e){LOG.warn("Coupon {} reservation pending reconciliation",r.coupon());}
    }
    private void finishPending(long coupon,long user,boolean committed) {
        try { var pending=gate.pending(coupon,user);if(pending!=null)finish(pending,committed);else if(!committed)invalidate(coupon); }
        catch(RuntimeException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"抢券结果正在恢复，请稍后重试");}
    }
    public void recoverExpired() {
        if(!enabled)return;
        for(long[] ids:gate.expired(200)) {
            RLock lock=clients.getObject().getLock(lockKey(ids[0],ids[1]));
            try {
                if(!lock.tryLock())continue;
                try { finishPending(ids[0],ids[1],store.find(ids[1],ids[0])!=null); }
                finally { unlock(lock); }
            } catch(RuntimeException e){LOG.warn("Coupon {} recovery deferred ({})",ids[0],e.getClass().getSimpleName());}
        }
    }
    private void unlock(RLock lock) {
        try { if(lock.isHeldByCurrentThread())lock.unlock(); }
        catch(RuntimeException e){LOG.debug("Coupon mutex will be released by watchdog timeout");}
    }
    private ApiException busy(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"领取请求正在处理，请稍后重试");}
}
