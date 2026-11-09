package orderhub.v1.refund;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import orderhub.v1.common.*;
import orderhub.v1.order.*;
import orderhub.v1.refund.entity.Refund;
import orderhub.v1.refund.mapper.RefundMapper;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin/refunds") @Tag(name="V1 退款审批")
public class RefundAdminController {
    private final OrderService orders;private final RefundMapper refunds;public RefundAdminController(OrderService orders,RefundMapper refunds){this.orders=orders;this.refunds=refunds;}
    @GetMapping public PageResult<Refund> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String status){var q=new QueryWrapper<Refund>();if(status!=null)q.eq("status",status);return PageResult.of(refunds,page,size,q.orderByDesc("id"));}
    @GetMapping("/{id}") public Refund detail(@PathVariable long id){return orders.refund(Actor.current(),id);}
    @PostMapping("/{id}/approve") public Refund approve(@PathVariable long id,@Valid @RequestBody OrderDtos.Reason in){return orders.resolveRefund(Actor.current(),id,"APPROVE",in.reason());}
    @PostMapping("/{id}/reject") public Refund reject(@PathVariable long id,@Valid @RequestBody OrderDtos.Reason in){return orders.resolveRefund(Actor.current(),id,"REJECT",in.reason());}
    @PostMapping("/{id}/complete") public Refund complete(@PathVariable long id){return orders.resolveRefund(Actor.current(),id,"APPROVE","模拟退款完成");}
}
