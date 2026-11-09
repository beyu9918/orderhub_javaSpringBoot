package orderhub.v1.coupon;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import orderhub.v1.common.*;
import orderhub.v1.coupon.entity.Coupon;
import orderhub.v1.coupon.mapper.CouponMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin/coupons") @Tag(name="V1 优惠券管理")
public class CouponAdminController {
    private final CouponService coupons;private final CouponMapper rows;public CouponAdminController(CouponService coupons,CouponMapper rows){this.coupons=coupons;this.rows=rows;}
    @GetMapping public PageResult<Coupon> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return PageResult.of(rows,page,size,new QueryWrapper<Coupon>().orderByDesc("id"));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Coupon add(@Valid @RequestBody CouponInput in){return coupons.save(Actor.current(),null,in);}
    @PutMapping("/{id}") public Coupon edit(@PathVariable long id,@Valid @RequestBody CouponInput in){return coupons.save(Actor.current(),id,in);}
    @PutMapping("/{id}/enable") @ResponseStatus(HttpStatus.NO_CONTENT) public void enable(@PathVariable long id){coupons.status(Actor.current(),id,true);}
    @PutMapping("/{id}/disable") @ResponseStatus(HttpStatus.NO_CONTENT) public void disable(@PathVariable long id){coupons.status(Actor.current(),id,false);}
}
