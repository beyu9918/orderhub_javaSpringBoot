package orderhub.v1.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import orderhub.api.ApiException;
import orderhub.v1.address.AddressService;
import orderhub.v1.audit.AuditService;
import orderhub.v1.cart.CartService;
import orderhub.v1.cart.entity.CartItem;
import orderhub.v1.cart.mapper.CartItemMapper;
import orderhub.v1.common.*;
import orderhub.v1.coupon.CouponService;
import orderhub.v1.coupon.entity.UserCoupon;
import orderhub.v1.coupon.mapper.UserCouponMapper;
import orderhub.v1.inventory.InventoryService;
import orderhub.v1.order.entity.*;
import orderhub.v1.order.mapper.*;
import orderhub.v1.payment.entity.Payment;
import orderhub.v1.payment.mapper.PaymentMapper;
import orderhub.v1.product.CatalogService;
import orderhub.v1.product.entity.*;
import orderhub.v1.product.mapper.*;
import orderhub.v1.refund.entity.Refund;
import orderhub.v1.refund.mapper.RefundMapper;
import orderhub.v1.shipment.entity.Shipment;
import orderhub.v1.shipment.mapper.ShipmentMapper;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All monetary and fulfillment transitions are serialized on the owning order row. */
@Service
public class OrderService {
    public record Detail(PurchaseOrder order,List<OrderItem> items){}
    private final PurchaseOrderMapper orders;private final OrderItemMapper items;private final StatusLogMapper statuses;
    private final AccountMapper users;private final CartService carts;private final CartItemMapper cartItems;
    private final ProductMapper products;private final SkuMapper skus;private final CatalogService catalog;
    private final AddressService addresses;private final InventoryService inventory;private final CouponService coupons;private final UserCouponMapper userCoupons;
    private final PaymentMapper payments;private final ShipmentMapper shipments;private final RefundMapper refunds;
    private final AuditService audit;private final CommerceCache cache;private final ApplicationEventPublisher events;
    public OrderService(PurchaseOrderMapper orders,OrderItemMapper items,StatusLogMapper statuses,AccountMapper users,CartService carts,CartItemMapper cartItems,
        ProductMapper products,SkuMapper skus,CatalogService catalog,AddressService addresses,InventoryService inventory,CouponService coupons,UserCouponMapper userCoupons,
        PaymentMapper payments,ShipmentMapper shipments,RefundMapper refunds,AuditService audit,CommerceCache cache,ApplicationEventPublisher events){
        this.orders=orders;this.items=items;this.statuses=statuses;this.users=users;this.carts=carts;this.cartItems=cartItems;this.products=products;this.skus=skus;this.catalog=catalog;
        this.addresses=addresses;this.inventory=inventory;this.coupons=coupons;this.userCoupons=userCoupons;this.payments=payments;this.shipments=shipments;this.refunds=refunds;
        this.audit=audit;this.cache=cache;this.events=events;
    }
    public Detail detail(Actor actor,long id){return view(owned(actor,id,false));}
    public List<OrderItem> lines(long id){return items.selectList(new QueryWrapper<OrderItem>().eq("order_id",id).orderByAsc("sku_id"));}
    private Detail view(PurchaseOrder o){return new Detail(o,lines(o.id));}
    public PurchaseOrder owned(Actor actor,long id,boolean lock){
        QueryWrapper<PurchaseOrder> q=new QueryWrapper<PurchaseOrder>().eq("id",id);
        if(!actor.admin()&&actor.id()!=null)q.eq("user_id",actor.id());
        if(lock)q.last("FOR UPDATE");return Db.require(orders.selectOne(q));
    }
    @Transactional public Detail checkout(Actor actor,String key,OrderDtos.Checkout in){
        Db.check(key!=null&&key.matches("[A-Za-z0-9_-]{8,64}"),"Idempotency-Key 必须为 8–64 个字母、数字、下划线或短横线");
        String hash=fingerprint(in);String cacheKey="order:idempotency:"+actor.id()+":"+key;
        String cached=cache.get(cacheKey);
        if(cached!=null)try{PurchaseOrder o=orders.selectById(Long.parseLong(cached));if(o!=null&&o.userId.equals(actor.id())&&o.requestKey.equals(key)){sameRequest(o,hash);return view(o);}}catch(NumberFormatException ignored){}
        Db.lock(users,actor.id());
        PurchaseOrder existing=orders.selectOne(new QueryWrapper<PurchaseOrder>().eq("user_id",actor.id()).eq("request_key",key));
        if(existing!=null){sameRequest(existing,hash);remember(cacheKey,existing.id);return view(existing);}
        var address=addresses.owned(actor.id(),in.addressId());
        long cartId=carts.cart(actor.id()).id;
        List<CartItem> selected=cartItems.selectList(new QueryWrapper<CartItem>().eq("cart_id",cartId).eq("selected",true).orderByAsc("sku_id"));
        Db.check(!selected.isEmpty(),"没有勾选购物车商品");
        Map<Long,Sku> locked=lockSkus(selected.stream().map(i->i.skuId).toList());
        List<OrderItem> snapshots=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
        for(CartItem row:selected){
            Sku sku=locked.get(row.skuId);Product product=Db.require(products.selectById(sku.productId));
            Db.check("ACTIVE".equals(sku.status)&&"ACTIVE".equals(product.status)&&catalog.categoryActive(product.categoryId),"购物车包含未上架商品");
            OrderItem line=new OrderItem();line.productId=product.id;line.skuId=sku.id;line.productName=product.name;line.skuAttributes=sku.attributes;line.productImage=product.imageUrl;
            line.unitPrice=sku.price;line.quantity=row.quantity;line.subtotal=money(sku.price.multiply(BigDecimal.valueOf(row.quantity)));snapshots.add(line);subtotal=subtotal.add(line.subtotal);
        }
        subtotal=money(subtotal);BigDecimal discount=coupons.discount(actor.id(),in.userCouponId(),subtotal);
        PurchaseOrder o=new PurchaseOrder();o.orderNo=number("ORD");o.userId=actor.id();o.addressId=address.id;o.addressSnapshot=JsonbHandler.object(address);
        o.requestKey=key;o.requestHash=hash;o.status="PENDING_PAYMENT";o.subtotalAmount=subtotal;o.discountAmount=discount;o.shippingAmount=BigDecimal.ZERO.setScale(2);o.payableAmount=money(subtotal.subtract(discount));o.remark=in.remark();
        if(in.userCouponId()!=null)o.couponId=Db.require(userCoupons.selectById(in.userCouponId())).couponId;
        orders.insert(o);
        for(OrderItem line:snapshots){line.orderId=o.id;items.insert(line);inventory.reserve(actor,line.skuId,o.id,line.quantity);}
        coupons.use(in.userCouponId(),o.id);
        cartItems.delete(new QueryWrapper<CartItem>().in("id",selected.stream().map(i->i.id).toList()));
        o=orders.selectById(o.id);record(actor,o,null,"创建订单",null);remember(cacheKey,o.id);return view(o);
    }
    private void sameRequest(PurchaseOrder o,String hash){Db.check(o.requestHash.equals(hash),"该幂等键已用于不同请求，请为新订单更换 Idempotency-Key");}
    private void remember(String key,long id){cache.afterCommit(()->cache.put(key,Long.toString(id),Duration.ofDays(1)));}
    private String fingerprint(Object input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonbHandler.encode(input).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private BigDecimal money(BigDecimal amount){Db.check(amount.signum()>=0&&amount.compareTo(new BigDecimal("9999999999.99"))<=0,"订单金额超过支持范围");return amount.setScale(2,RoundingMode.HALF_UP);}
    private String number(String prefix){return prefix+UUID.randomUUID().toString().replace("-","");}
    private Map<Long,Sku> lockSkus(List<Long> ids){
        List<Sku> initial=skus.selectByIds(ids);Db.check(initial.size()==new HashSet<>(ids).size(),"SKU 不存在");
        initial.stream().map(s->s.productId).distinct().sorted().forEach(id->Db.lock(products,id));
        Map<Long,Sku> result=new TreeMap<>();ids.stream().distinct().sorted().forEach(id->result.put(id,Db.lock(skus,id)));return result;
    }
    private void inventory(Actor actor,PurchaseOrder o,boolean complete,boolean refund){
        List<OrderItem> rows=lines(o.id);lockSkus(rows.stream().map(i->i.skuId).toList());
        for(OrderItem i:rows){if(complete)inventory.complete(actor,i.skuId,o.id,i.quantity);else inventory.release(actor,i.skuId,o.id,i.quantity,refund);}
    }
    @Transactional public Detail cancel(Actor actor,long id,String reason){
        PurchaseOrder o=owned(actor,id,true);if("CANCELLED".equals(o.status))return view(o);
        Db.check("PENDING_PAYMENT".equals(o.status),"只有未付款订单可以取消");var before=JsonbHandler.object(o);
        inventory(actor,o,false,false);coupons.release(o.id);o.cancelReason=reason;o.cancelledAt=Db.now();
        payments.update(null,new UpdateWrapper<Payment>().eq("order_id",id).eq("status","PENDING").set("status","FAILED").set("updated_at",Db.now()));
        move(actor,o,Set.of("PENDING_PAYMENT"),"CANCELLED",reason,"ORDER_CANCELLED");audit.record(actor,"CANCEL_ORDER","ORDER",id,before,o);return view(o);
    }
    @Transactional public void cancelExpired(long id){
        PurchaseOrder o=owned(Actor.system(),id,true);
        if("PENDING_PAYMENT".equals(o.status)&&o.createdAt.isBefore(Db.now().minusMinutes(30)))cancel(Actor.system(),id,"未付款超过 30 分钟");
    }
    @Transactional public Detail process(Actor actor,long id){
        PurchaseOrder o=owned(actor,id,true);if("PROCESSING".equals(o.status))return view(o);var before=JsonbHandler.object(o);
        move(actor,o,Set.of("PAID"),"PROCESSING","管理员确认处理","ORDER_PROCESSING");audit.record(actor,"PROCESS_ORDER","ORDER",id,before,o);return view(o);
    }
    @Transactional public Shipment ship(Actor actor,long id,OrderDtos.Ship input){
        PurchaseOrder o=owned(actor,id,true);Shipment existing=shipments.selectOne(new QueryWrapper<Shipment>().eq("order_id",id).eq("tracking_number",input.trackingNumber()));
        if(existing!=null){Db.check(existing.carrier.equals(input.carrier())&&existing.trackingNumber.equals(input.trackingNumber()),"订单已使用其他物流单发货");return existing;}
        Db.check(Set.of("PROCESSING","SHIPPED").contains(o.status),"只有处理中或运输中的订单可以发货");var before=JsonbHandler.object(o);
        Shipment s=new Shipment();s.orderId=id;s.carrier=input.carrier();s.trackingNumber=input.trackingNumber();s.status="SHIPPED";s.shippedAt=Db.now();shipments.insert(s);
        if("PROCESSING".equals(o.status)){o.shippedAt=s.shippedAt;move(actor,o,Set.of("PROCESSING"),"SHIPPED","发货","ORDER_SHIPPED");}
        audit.record(actor,"SHIP_ORDER","ORDER",id,before,o);return shipments.selectById(s.id);
    }
    public List<Shipment> shipment(Actor actor,long id){owned(actor,id,false);return shipments.selectList(new QueryWrapper<Shipment>().eq("order_id",id).orderByAsc("id"));}
    @Transactional public Shipment shipmentStatus(Actor actor,long id,String status){
        Shipment original=Db.require(shipments.selectById(id));PurchaseOrder o=owned(actor,original.orderId,true);Shipment s=Db.lock(shipments,id);
        if(s.status.equals(status))return s;var before=JsonbHandler.object(s);
        Db.check("SHIPPED".equals(o.status)&&Set.of("SHIPPED","IN_TRANSIT").contains(s.status),"物流状态不能倒退");
        Db.check(Set.of("IN_TRANSIT","DELIVERED").contains(status),"不支持的物流状态");s.status=status;s.updatedAt=Db.now();
        if("DELIVERED".equals(status))s.deliveredAt=Db.now();
        shipments.updateById(s);
        if("DELIVERED".equals(status)&&shipments.selectCount(new QueryWrapper<Shipment>().eq("order_id",o.id).ne("status","DELIVERED"))==0){
            o.deliveredAt=s.deliveredAt;move(actor,o,Set.of("SHIPPED"),"DELIVERED","全部包裹已送达","ORDER_DELIVERED");
        }
        audit.record(actor,"UPDATE_SHIPMENT","SHIPMENT",id,before,s);return s;
    }
    @Transactional public Detail complete(Actor actor,long id){
        PurchaseOrder o=owned(actor,id,true);if("COMPLETED".equals(o.status))return view(o);
        Db.check("DELIVERED".equals(o.status),"只有已送达订单可以确认收货");inventory(actor,o,true,false);o.completedAt=Db.now();
        move(actor,o,Set.of("DELIVERED"),"COMPLETED","确认收货","ORDER_COMPLETED");return view(o);
    }
    @Transactional public void autoComplete(long id){
        PurchaseOrder o=owned(Actor.system(),id,true);if("DELIVERED".equals(o.status)&&o.deliveredAt!=null&&o.deliveredAt.isBefore(Db.now().minusDays(7)))complete(Actor.system(),id);
    }
    @Transactional public CartService.View buyAgain(Actor actor,long id){
        Db.lock(users,actor.id());PurchaseOrder o=owned(actor,id,false);for(OrderItem line:lines(o.id))carts.add(actor.id(),line.skuId,line.quantity);return carts.view(actor.id());
    }
    @Transactional public Payment createPayment(Actor actor,long id,String method){
        Db.check("MOCK".equals(method),"V1 仅支持 MOCK 支付");PurchaseOrder o=owned(actor,id,true);payable(o);
        Payment existing=payments.selectOne(new QueryWrapper<Payment>().eq("order_id",id).eq("status","PENDING").orderByDesc("id").last("LIMIT 1"));if(existing!=null)return existing;
        Payment p=new Payment();p.orderId=id;p.paymentNo=number("PAY");p.amount=o.payableAmount;p.method="MOCK";p.status="PENDING";payments.insert(p);return payments.selectById(p.id);
    }
    public List<Payment> payments(Actor actor,long id){owned(actor,id,false);return payments.selectList(new QueryWrapper<Payment>().eq("order_id",id).orderByAsc("id"));}
    @Transactional public Payment paymentResult(Actor actor,long id,boolean success){
        Payment initial=Db.require(payments.selectById(id));PurchaseOrder o=owned(actor,initial.orderId,true);Payment p=Db.lock(payments,id);
        if(success&&Set.of("SUCCESS","REFUNDED").contains(p.status))return p;
        if(!success&&"FAILED".equals(p.status))return p;
        Db.check("PENDING".equals(p.status),"支付尝试已结束");payable(o);
        p.status=success?"SUCCESS":"FAILED";p.updatedAt=Db.now();
        if(success){
            Db.check(p.amount.compareTo(o.payableAmount)==0,"支付金额不匹配");p.transactionId=number("MOCK");p.paidAt=Db.now();o.paidAt=p.paidAt;
            payments.update(null,new UpdateWrapper<Payment>().eq("order_id",o.id).eq("status","PENDING").ne("id",id).set("status","FAILED").set("updated_at",Db.now()));
            move(actor,o,Set.of("PENDING_PAYMENT"),"PAID","模拟支付成功","ORDER_PAID");
        }
        payments.updateById(p);return p;
    }
    private void payable(PurchaseOrder o){Db.check("PENDING_PAYMENT".equals(o.status),"订单不是待付款状态");Db.check(o.createdAt.plusMinutes(30).isAfter(Db.now()),"订单支付期限已过");}
    @Transactional public Refund requestRefund(Actor actor,long id,String reason){
        PurchaseOrder o=owned(actor,id,true);Db.check(Set.of("PAID","PROCESSING").contains(o.status),"只允许已付款且未发货的订单申请退款");
        Payment p=Db.require(payments.selectOne(new QueryWrapper<Payment>().eq("order_id",id).eq("status","SUCCESS")));
        Refund r=new Refund();r.refundNo=number("REF");r.orderId=id;r.userId=o.userId;r.paymentId=p.id;r.amount=o.payableAmount;r.reason=reason;r.status="REQUESTED";r.previousOrderStatus=o.status;refunds.insert(r);
        move(actor,o,Set.of("PAID","PROCESSING"),"REFUND_REQUESTED",reason,null);return refunds.selectById(r.id);
    }
    public Refund refund(Actor actor,long id){
        QueryWrapper<Refund> q=new QueryWrapper<Refund>().eq("id",id);if(!actor.admin())q.eq("user_id",actor.id());return Db.require(refunds.selectOne(q));
    }
    public List<Refund> refunds(Actor actor,long id){owned(actor,id,false);return refunds.selectList(new QueryWrapper<Refund>().eq("order_id",id).orderByDesc("id"));}
    @Transactional public Refund resolveRefund(Actor actor,long id,String decision,String remark){
        Refund initial=refund(actor,id);PurchaseOrder o=owned(actor,initial.orderId,true);Refund r=Db.lock(refunds,id);
        if("APPROVE".equals(decision)&&"COMPLETED".equals(r.status))return r;
        if("CANCEL".equals(decision)&&"CANCELLED".equals(r.status))return r;
        if("REJECT".equals(decision)&&"REJECTED".equals(r.status))return r;
        Db.check("REQUESTED".equals(r.status)&&"REFUND_REQUESTED".equals(o.status),"退款申请已处理");var before=JsonbHandler.object(r);
        if("APPROVE".equals(decision)){
            Db.check(actor.admin(),"需要管理员权限");Payment p=Db.lock(payments,r.paymentId);Db.check("SUCCESS".equals(p.status),"支付记录不可退款");
            inventory(actor,o,false,true);coupons.release(o.id);p.status="REFUNDED";p.updatedAt=Db.now();payments.updateById(p);
            r.status="COMPLETED";r.approvedAt=Db.now();r.completedAt=r.approvedAt;r.adminRemark=remark;
            move(actor,o,Set.of("REFUND_REQUESTED"),"REFUNDED","模拟退款完成","REFUND_APPROVED");
        }else{
            Db.check("CANCEL".equals(decision)||actor.admin(),"需要管理员权限");r.status="CANCEL".equals(decision)?"CANCELLED":"REJECTED";r.adminRemark=remark;
            move(actor,o,Set.of("REFUND_REQUESTED"),r.previousOrderStatus,remark,"REJECT".equals(decision)?"REFUND_REJECTED":null);
        }
        r.updatedAt=Db.now();refunds.updateById(r);audit.record(actor,"REFUND_"+decision,"REFUND",id,before,r);return r;
    }
    public PageResult<PurchaseOrder> list(Actor actor,OrderDtos.Query in){
        QueryWrapper<PurchaseOrder> q=new QueryWrapper<>();if(!actor.admin())q.eq("user_id",actor.id());else if(in.userId()!=null)q.eq("user_id",in.userId());
        if(in.orderNo()!=null)q.eq("order_no",in.orderNo());if(in.status()!=null){Db.check(STATES.contains(in.status()),"无效订单状态");q.eq("status",in.status());}
        if(in.startDate()!=null)q.ge("created_at",in.startDate());if(in.endDate()!=null)q.le("created_at",in.endDate());
        Db.check(in.startDate()==null||in.endDate()==null||!in.startDate().isAfter(in.endDate()),"起始时间不能晚于结束时间");
        if(in.minAmount()!=null)q.ge("payable_amount",in.minAmount());if(in.maxAmount()!=null)q.le("payable_amount",in.maxAmount());
        Db.check(in.minAmount()==null||in.maxAmount()==null||in.minAmount().compareTo(in.maxAmount())<=0,"金额区间无效");
        return PageResult.of(orders,in.page(),in.size(),q.orderByDesc("created_at","id"));
    }
    public List<StatusLog> history(Actor actor,long id){owned(actor,id,false);return statuses.selectList(new QueryWrapper<StatusLog>().eq("order_id",id).orderByAsc("id"));}
    private static final Set<String> STATES=Set.of("PENDING_PAYMENT","PAID","PROCESSING","SHIPPED","DELIVERED","COMPLETED","CANCELLED","REFUND_REQUESTED","REFUNDED");
    private void move(Actor actor,PurchaseOrder o,Set<String> expected,String target,String reason,String event){
        Db.check(expected.contains(o.status),"非法订单状态变化："+o.status+" → "+target);String from=o.status;o.status=target;o.updatedAt=Db.now();orders.updateById(o);record(actor,o,from,reason,event);
    }
    private void record(Actor actor,PurchaseOrder o,String from,String reason,String event){
        StatusLog log=new StatusLog();log.orderId=o.id;log.fromStatus=from;log.toStatus=o.status;log.operatorType=actor.type();log.operatorId=actor.id();log.reason=reason;statuses.insert(log);
        cache.evict("dashboard:overview");if(event!=null)events.publishEvent(new OrderEvent(o.userId,o.id,o.orderNo,o.status,event,Db.now()));
    }
}
