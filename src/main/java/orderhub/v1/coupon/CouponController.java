package orderhub.v1.coupon;

import java.util.List;
import orderhub.v1.common.Actor;
import orderhub.v1.coupon.entity.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user") @Tag(name="V1 用户优惠券")
public class CouponController {
    private final CouponService coupons;public CouponController(CouponService coupons){this.coupons=coupons;}
    @GetMapping("/coupons/available") public List<Coupon> available(){return coupons.available();}
    @PostMapping("/coupons/{couponId}/claim") @ResponseStatus(HttpStatus.CREATED) public UserCoupon claim(@PathVariable long couponId){return coupons.claim(Actor.current().id(),couponId);}
    @GetMapping("/my-coupons") public List<UserCoupon> mine(){return coupons.mine(Actor.current().id());}
}
