package orderhub.v1.coupon.claim;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @EnableScheduling
public class CouponClaimRecovery {
    private static final Logger LOG=LoggerFactory.getLogger(CouponClaimRecovery.class);
    private final CouponClaimCoordinator coordinator;
    private final boolean enabled;
    public CouponClaimRecovery(CouponClaimCoordinator coordinator,@Value("${orderhub.v1.tasks.enabled}") boolean enabled) {
        this.coordinator=coordinator;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${orderhub.coupon.recovery-delay-ms}",initialDelay=60000)
    public void recover(){if(enabled)try{coordinator.recoverExpired();}catch(RuntimeException e){LOG.warn("Coupon recovery unavailable: {}",e.getClass().getSimpleName());}}
}
