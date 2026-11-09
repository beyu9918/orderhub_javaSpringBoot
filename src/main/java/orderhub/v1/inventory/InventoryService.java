package orderhub.v1.inventory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import orderhub.v1.audit.AuditService;
import orderhub.v1.common.*;
import orderhub.v1.inventory.entity.*;
import orderhub.v1.inventory.mapper.*;
import orderhub.v1.product.mapper.SkuMapper;
import orderhub.v1.product.mapper.ProductMapper;
import orderhub.v1.product.CatalogDtos.Adjustment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final StockMapper stocks;private final StockLogMapper logs;private final SkuMapper skus;private final ProductMapper products;private final AuditService audit;
    public InventoryService(StockMapper stocks,StockLogMapper logs,SkuMapper skus,ProductMapper products,AuditService audit){this.stocks=stocks;this.logs=logs;this.skus=skus;this.products=products;this.audit=audit;}
    public Stock get(long sku){return Db.require(stocks.selectOne(new QueryWrapper<Stock>().eq("sku_id",sku)));}
    public void initialize(Actor actor,long sku,int quantity){
        Stock s=new Stock();s.skuId=sku;s.availableStock=quantity;s.reservedStock=0;s.version=0;stocks.insert(s);
        log(actor,s,null,"PURCHASE",quantity,0,0,"初始入库");
    }
    public void reserve(Actor actor,long sku,long order,int quantity){change(actor,sku,order,quantity,"ORDER_RESERVED",-quantity,quantity,"下单预占");}
    public void release(Actor actor,long sku,long order,int quantity,boolean refund){change(actor,sku,order,quantity,refund?"REFUND_RETURN":"ORDER_CANCELLED",quantity,-quantity,"释放预占库存");}
    public void complete(Actor actor,long sku,long order,int quantity){change(actor,sku,order,quantity,"ORDER_COMPLETED",0,-quantity,"订单完成，扣除预占");}
    private void change(Actor actor,long sku,long order,int qty,String type,int available,int reserved,String remark){
        Stock before=Db.require(stocks.selectOne(new QueryWrapper<Stock>().eq("sku_id",sku).last("FOR UPDATE")));
        Db.check((long)before.availableStock+available>=0&&(long)before.availableStock+available<=1000000000L,"库存不足或超过上限");
        Db.check((long)before.reservedStock+reserved>=0&&(long)before.reservedStock+reserved<=1000000000L,"预占库存无效");
        int changed=stocks.update(null,new UpdateWrapper<Stock>().eq("id",before.id).eq("version",before.version)
            .ge("available_stock",Math.max(0,-available)).ge("reserved_stock",Math.max(0,-reserved))
            .setSql("available_stock = available_stock + "+available).setSql("reserved_stock = reserved_stock + "+reserved)
            .setSql("version = version + 1").set("updated_at",Db.now()));
        Db.check(changed==1,"库存不足或版本已变化");Stock after=get(sku);log(actor,after,order,type,qty,before.availableStock,before.reservedStock,remark);
    }
    @Transactional public Stock adjust(Actor actor,long sku,Adjustment input){
        var s=Db.require(skus.selectById(sku));Db.lock(products,s.productId);Db.lock(skus,sku);Stock before=get(sku);
        Db.check(before.version.equals(input.expectedVersion()),"库存版本已更新，请刷新后重试");
        change(actor,sku,0,input.quantity(),"ADMIN_ADJUSTMENT",input.quantity(),0,input.reason());
        Stock after=get(sku);audit.record(actor,"UPDATE_INVENTORY","SKU",sku,before,after);return after;
    }
    private void log(Actor actor,Stock after,Long order,String type,int qty,int before,int reserved,String remark){
        StockLog l=new StockLog();l.skuId=after.skuId;l.orderId=order==null||order==0?null:order;l.changeType=type;l.quantity=qty;l.beforeStock=before;l.afterStock=after.availableStock;
        l.beforeReserved=reserved;l.afterReserved=after.reservedStock;l.operatorId=actor.id();l.remark=remark;logs.insert(l);
    }
}
