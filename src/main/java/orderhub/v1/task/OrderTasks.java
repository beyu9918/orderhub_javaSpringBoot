package orderhub.v1.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.function.LongConsumer;
import orderhub.v1.common.Db;
import orderhub.v1.coupon.CouponService;
import orderhub.v1.order.OrderService;
import orderhub.v1.order.entity.PurchaseOrder;
import orderhub.v1.order.mapper.PurchaseOrderMapper;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @EnableScheduling
@ConditionalOnProperty(name="orderhub.v1.tasks.enabled",havingValue="true",matchIfMissing=true)
public class OrderTasks {
    private static final Logger LOG=LoggerFactory.getLogger(OrderTasks.class);
    private final PurchaseOrderMapper rows;private final OrderService orders;private final CouponService coupons;
    public OrderTasks(PurchaseOrderMapper rows,OrderService orders,CouponService coupons){this.rows=rows;this.orders=orders;this.coupons=coupons;}
    @Scheduled(fixedDelayString="${orderhub.v1.tasks.delay-ms:60000}",initialDelay=60000)
    public void run(){
        scan(new QueryWrapper<PurchaseOrder>().eq("status","PENDING_PAYMENT").lt("created_at",Db.now().minusMinutes(30)),orders::cancelExpired);
        scan(new QueryWrapper<PurchaseOrder>().eq("status","DELIVERED").lt("delivered_at",Db.now().minusDays(7)),orders::autoComplete);
        coupons.expire();
    }
    private void scan(QueryWrapper<PurchaseOrder> query,LongConsumer action){
        for(PurchaseOrder o:rows.selectList(query.orderByAsc("id").last("LIMIT 200")))
            try{action.accept(o.id);}catch(RuntimeException ex){LOG.warn("Scheduled order {} failed: {}",o.id,ex.getClass().getSimpleName());}
    }
}
