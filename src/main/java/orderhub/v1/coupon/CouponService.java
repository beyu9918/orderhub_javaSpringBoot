package orderhub.v1.coupon;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.*;
import java.util.*;
import orderhub.v1.audit.AuditService;
import orderhub.v1.common.*;
import orderhub.v1.coupon.entity.*;
import orderhub.v1.coupon.mapper.*;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private final CouponMapper coupons;private final UserCouponMapper owned;private final AccountMapper users;private final AuditService audit;
    public CouponService(CouponMapper coupons,UserCouponMapper owned,AccountMapper users,AuditService audit){this.coupons=coupons;this.owned=owned;this.users=users;this.audit=audit;}
    public List<Coupon> available(){return coupons.selectList(new QueryWrapper<Coupon>().eq("status","ACTIVE").le("start_at",Db.now()).gt("expire_at",Db.now()).apply("claimed_quantity < total_quantity").orderByDesc("id"));}
    public List<UserCoupon> mine(long user){return owned.selectList(new QueryWrapper<UserCoupon>().eq("user_id",user).orderByDesc("id"));}
    @Transactional public UserCoupon claim(long user,long id){
        Db.lock(users,user);Coupon c=Db.lock(coupons,id);
        UserCoupon existing=owned.selectOne(new QueryWrapper<UserCoupon>().eq("user_id",user).eq("coupon_id",id));if(existing!=null)return existing;
        Db.check("ACTIVE".equals(c.status)&&!c.startAt.isAfter(Db.now())&&c.expireAt.isAfter(Db.now()),"优惠券不在可领取时段");
        int changed=coupons.update(null,new UpdateWrapper<Coupon>().eq("id",id).apply("claimed_quantity < total_quantity").setSql("claimed_quantity = claimed_quantity + 1"));
        Db.check(changed==1,"优惠券已领完");UserCoupon uc=new UserCoupon();uc.userId=user;uc.couponId=id;uc.status="AVAILABLE";uc.expireAt=c.expireAt;owned.insert(uc);return owned.selectById(uc.id);
    }
    public BigDecimal discount(long user,Long userCouponId,BigDecimal subtotal){
        if(userCouponId==null)return BigDecimal.ZERO.setScale(2);
        UserCoupon uc=Db.lock(owned,userCouponId);Db.check(uc.userId==user&&"AVAILABLE".equals(uc.status)&&uc.expireAt.isAfter(Db.now()),"优惠券不可用");
        Coupon c=Db.lock(coupons,uc.couponId);Db.check("ACTIVE".equals(c.status)&&!c.startAt.isAfter(Db.now())&&c.expireAt.isAfter(Db.now())&&subtotal.compareTo(c.minimumAmount)>=0,"优惠券未满足使用条件");
        BigDecimal amount="FIXED".equals(c.discountType)?c.discountValue:subtotal.multiply(c.discountValue).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
        return amount.min(subtotal).setScale(2,RoundingMode.HALF_UP);
    }
    public Long use(Long id,long order){
        if(id==null)return null;UserCoupon uc=Db.lock(owned,id);uc.status="USED";uc.orderId=order;uc.usedAt=Db.now();owned.updateById(uc);return uc.couponId;
    }
    public void release(long order){
        UserCoupon uc=owned.selectOne(new QueryWrapper<UserCoupon>().eq("order_id",order).last("FOR UPDATE"));if(uc==null)return;
        owned.update(null,new UpdateWrapper<UserCoupon>().eq("id",uc.id).set("status",uc.expireAt.isAfter(Db.now())?"AVAILABLE":"EXPIRED").set("order_id",null).set("used_at",null));
    }
    @Transactional public Coupon save(Actor actor,Long id,CouponInput in){
        Db.check(in.expireAt().isAfter(in.startAt()),"过期时间必须晚于开始时间");
        Db.check(!"PERCENTAGE".equals(in.discountType())||in.discountValue().compareTo(BigDecimal.valueOf(100))<=0,"百分比是 0–100 的优惠比例，例如 10 表示减免 10%");
        Coupon c=id==null?new Coupon():Db.lock(coupons,id);Object before=id==null?null:JsonbHandler.object(c);
        if(id!=null){Db.check(c.claimedQuantity==0,"已领取的优惠券不可编辑，只能启用或停用");}
        c.code=in.code();c.name=in.name();c.discountType=in.discountType();c.discountValue=in.discountValue();c.minimumAmount=in.minimumAmount();c.totalQuantity=in.totalQuantity();c.startAt=in.startAt();c.expireAt=in.expireAt();
        if(id==null){c.status="ACTIVE";c.claimedQuantity=0;coupons.insert(c);}else coupons.updateById(c);
        audit.record(actor,"SAVE_COUPON","COUPON",c.id,before,c);return coupons.selectById(c.id);
    }
    @Transactional public void status(Actor actor,long id,boolean enabled){Coupon c=Db.lock(coupons,id);var before=JsonbHandler.object(c);c.status=enabled?"ACTIVE":"INACTIVE";coupons.updateById(c);audit.record(actor,"COUPON_STATUS","COUPON",id,before,c);}
    @Transactional public int expire(){return owned.update(null,new UpdateWrapper<UserCoupon>().eq("status","AVAILABLE").le("expire_at",Db.now()).set("status","EXPIRED"));}
}
