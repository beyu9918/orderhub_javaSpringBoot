package orderhub.v1.coupon.claim;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.List;
import orderhub.v1.common.Db;
import orderhub.v1.coupon.entity.*;
import orderhub.v1.coupon.mapper.*;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CouponClaimStore {
    public record Snapshot(Coupon coupon, List<Long> users) {}
    private final CouponMapper coupons;
    private final UserCouponMapper owned;
    private final AccountMapper users;
    public CouponClaimStore(CouponMapper coupons, UserCouponMapper owned, AccountMapper users) {
        this.coupons=coupons; this.owned=owned; this.users=users;
    }
    public UserCoupon find(long user, long coupon) {
        return owned.selectOne(new QueryWrapper<UserCoupon>().eq("user_id",user).eq("coupon_id",coupon));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Snapshot snapshot(long id) {
        Coupon c=Db.require(coupons.selectById(id));
        List<Long> ids=owned.selectList(new QueryWrapper<UserCoupon>().select("user_id").eq("coupon_id",id))
            .stream().map(row->row.userId).toList();
        return new Snapshot(c,ids);
    }
    // A separate proxy/transaction ensures commit finishes BEFORE the distributed lock is released.
    @Transactional(propagation=Propagation.REQUIRES_NEW, timeout=10)
    public UserCoupon claim(long user, long id) {
        var account=Db.lock(users,user);
        Db.check("ACTIVE".equals(account.status),"账号已禁用");
        Coupon c=Db.lock(coupons,id);
        UserCoupon existing=find(user,id);
        if(existing!=null)return existing;
        Db.check("ACTIVE".equals(c.status)&&!c.startAt.isAfter(Db.now())&&c.expireAt.isAfter(Db.now()),"优惠券不在可领取时段");
        int changed=coupons.update(null,new UpdateWrapper<Coupon>().eq("id",id)
            .apply("claimed_quantity < total_quantity").setSql("claimed_quantity = claimed_quantity + 1"));
        Db.check(changed==1,"优惠券已领完");
        UserCoupon uc=new UserCoupon(); uc.userId=user; uc.couponId=id; uc.status="AVAILABLE"; uc.expireAt=c.expireAt;
        owned.insert(uc);
        return owned.selectById(uc.id);
    }
}
