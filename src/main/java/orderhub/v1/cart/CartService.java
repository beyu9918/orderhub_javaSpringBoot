package orderhub.v1.cart;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import orderhub.v1.cart.entity.*;
import orderhub.v1.cart.mapper.*;
import orderhub.v1.common.*;
import orderhub.v1.product.CatalogService;
import orderhub.v1.product.entity.*;
import orderhub.v1.product.mapper.*;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    public record Line(CartItem item,Sku sku,Product product){}
    public record View(long id,List<Line> items,BigDecimal selectedTotal){}
    private final CartMapper carts;private final CartItemMapper items;private final AccountMapper users;
    private final SkuMapper skus;private final ProductMapper products;private final CatalogService catalog;
    public CartService(CartMapper carts,CartItemMapper items,AccountMapper users,SkuMapper skus,ProductMapper products,CatalogService catalog){
        this.carts=carts;this.items=items;this.users=users;this.skus=skus;this.products=products;this.catalog=catalog;
    }
    public Cart cart(long user){return Db.require(carts.selectOne(new QueryWrapper<Cart>().eq("user_id",user)));}
    public View view(long user){
        Cart c=cart(user);List<CartItem> rows=items.selectList(new QueryWrapper<CartItem>().eq("cart_id",c.id).orderByAsc("id"));
        if(rows.isEmpty())return new View(c.id,List.of(),BigDecimal.ZERO);
        Map<Long,Sku> skuMap=skus.selectByIds(rows.stream().map(i->i.skuId).toList()).stream().collect(Collectors.toMap(s->s.id,s->s));
        Map<Long,Product> productMap=products.selectByIds(skuMap.values().stream().map(s->s.productId).distinct().toList()).stream().collect(Collectors.toMap(p->p.id,p->p));
        List<Line> result=rows.stream().map(i->new Line(i,skuMap.get(i.skuId),productMap.get(skuMap.get(i.skuId).productId))).toList();
        BigDecimal total=result.stream().filter(l->l.item.selected).map(l->l.sku.price.multiply(BigDecimal.valueOf(l.item.quantity))).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new View(c.id,result,total);
    }
    @Transactional public CartItem add(long user,long skuId,int quantity){
        Db.lock(users,user);Sku s=Db.require(skus.selectById(skuId));Product p=Db.require(products.selectById(s.productId));
        Db.check("ACTIVE".equals(s.status)&&"ACTIVE".equals(p.status)&&catalog.categoryActive(p.categoryId),"商品未上架");
        Cart c=cart(user);CartItem item=items.selectOne(new QueryWrapper<CartItem>().eq("cart_id",c.id).eq("sku_id",skuId));
        if(item==null){Db.check(items.selectCount(new QueryWrapper<CartItem>().eq("cart_id",c.id))<100,"购物车最多 100 种 SKU");
            item=new CartItem();item.cartId=c.id;item.skuId=skuId;item.quantity=quantity;item.selected=true;items.insert(item);
        }else{Db.check(item.quantity+quantity<=999,"单个 SKU 最多 999 件");item.quantity+=quantity;item.updatedAt=Db.now();items.updateById(item);}
        touch(c);return items.selectById(item.id);
    }
    @Transactional public void quantity(long user,long id,int quantity){Db.lock(users,user);CartItem i=owned(user,id);i.quantity=quantity;i.updatedAt=Db.now();items.updateById(i);touch(cart(user));}
    @Transactional public void select(long user,Long id,boolean selected){
        Db.lock(users,user);Cart c=cart(user);if(id!=null)owned(user,id);
        var q=new UpdateWrapper<CartItem>().eq("cart_id",c.id);if(id!=null)q.eq("id",id);
        items.update(null,q.set("selected",selected).set("updated_at",Db.now()));touch(c);
    }
    @Transactional public void delete(long user,Long id){
        Db.lock(users,user);Cart c=cart(user);if(id!=null)owned(user,id);
        QueryWrapper<CartItem> q=new QueryWrapper<CartItem>().eq("cart_id",c.id);if(id!=null)q.eq("id",id);items.delete(q);touch(c);
    }
    private CartItem owned(long user,long id){return Db.require(items.selectOne(new QueryWrapper<CartItem>().eq("id",id).eq("cart_id",cart(user).id)));}
    private void touch(Cart c){c.updatedAt=Db.now();carts.updateById(c);}
}
