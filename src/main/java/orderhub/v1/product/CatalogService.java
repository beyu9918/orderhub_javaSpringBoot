package orderhub.v1.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import orderhub.api.ApiException;
import orderhub.v1.audit.AuditService;
import orderhub.v1.category.entity.Category;
import orderhub.v1.category.mapper.CategoryMapper;
import orderhub.v1.common.*;
import orderhub.v1.product.entity.*;
import orderhub.v1.product.mapper.*;
import orderhub.v1.product.cache.ProductDetailCache;
import orderhub.v1.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    public record ProductDetail(Product product,List<Sku> skus){}
    public record SkuList(List<Sku> skus){}
    private final CategoryMapper categories;private final ProductMapper products;private final SkuMapper skus;
    private final InventoryService inventory;private final AuditService audit;private final CommerceCache cache;
    private final ProductDetailCache details;
    public CatalogService(CategoryMapper categories,ProductMapper products,SkuMapper skus,InventoryService inventory,AuditService audit,CommerceCache cache,ProductDetailCache details){
        this.categories=categories;this.products=products;this.skus=skus;this.inventory=inventory;this.audit=audit;this.cache=cache;
        this.details=details;
    }
    public List<Category> categories(boolean admin){
        List<Category> all=categories.selectList(new QueryWrapper<Category>().orderByAsc("sort_order","id"));
        if(admin)return all;
        Map<Long,Category> byId=new HashMap<>();all.forEach(c->byId.put(c.id,c));
        return all.stream().filter(c->active(c.id,byId)).toList();
    }
    private boolean active(long id,Map<Long,Category> all){
        Category c=all.get(id);Set<Long> visited=new HashSet<>();
        while(c!=null){if(!visited.add(c.id)||!"ACTIVE".equals(c.status))return false;if(c.parentId==null)return true;c=all.get(c.parentId);}return false;
    }
    @Transactional public Category category(Actor actor,Long id,CatalogDtos.CategoryInput in){
        // One global category lock order avoids two concurrent reparent operations creating a cycle.
        List<Category> all=categories.selectList(new QueryWrapper<Category>().orderByAsc("id").last("FOR UPDATE"));
        Category c=id==null?new Category():Db.require(categories.selectById(id));Object before=id==null?null:JsonbHandler.object(c);
        Map<Long,Category> byId=new HashMap<>();all.forEach(x->byId.put(x.id,x));
        Long parent=in.parentId();Set<Long> visited=new HashSet<>();
        while(parent!=null){Db.check(!Objects.equals(parent,id)&&visited.add(parent),"分类不能形成循环");parent=Db.require(byId.get(parent)).parentId;}
        c.name=in.name();c.parentId=in.parentId();c.sortOrder=in.sortOrder();c.updatedAt=Db.now();
        if(id==null){c.status="ACTIVE";categories.insert(c);}else categories.updateById(c);
        audit.record(actor,"SAVE_CATEGORY","CATEGORY",c.id,before,c);return categories.selectById(c.id);
    }
    @Transactional public void categoryStatus(Actor actor,long id,boolean enabled){
        Category c=Db.lock(categories,id);var before=JsonbHandler.object(c);c.status=enabled?"ACTIVE":"INACTIVE";c.updatedAt=Db.now();categories.updateById(c);
        // Visibility is checked against the live category even on cached product reads.
        audit.record(actor,"CATEGORY_STATUS","CATEGORY",id,before,c);
    }
    @Transactional public void deleteCategory(Actor actor,long id){
        Category c=Db.lock(categories,id);
        Db.check(categories.selectCount(new QueryWrapper<Category>().eq("parent_id",id))==0&&products.selectCount(new QueryWrapper<Product>().eq("category_id",id))==0,"分类仍有子分类或商品");
        categories.deleteById(id);audit.record(actor,"DELETE_CATEGORY","CATEGORY",id,c,null);
    }
    public ProductDetail detail(long id,boolean admin){
        if(admin)return new ProductDetail(Db.require(products.selectById(id)),skus.selectList(new QueryWrapper<Sku>().eq("product_id",id).orderByAsc("id")));
        ProductDetail detail=details.get(id,ProductDetail.class,()->{
            Product p=products.selectById(id);if(p==null||!"ACTIVE".equals(p.status))return null;
            return new ProductDetail(p,skus.selectList(new QueryWrapper<Sku>().eq("product_id",id).eq("status","ACTIVE").orderByAsc("id")));
        });
        if(detail==null)throw ApiException.missing("商品不存在或未上架");
        if(!categoryActive(detail.product.categoryId))throw ApiException.missing("商品分类未启用");
        cache.viewed(id);return detail;
    }
    public boolean categoryActive(long id){
        Category c=categories.selectById(id);Set<Long> seen=new HashSet<>();
        while(c!=null){if(!seen.add(c.id)||!"ACTIVE".equals(c.status))return false;if(c.parentId==null)return true;c=categories.selectById(c.parentId);}return false;
    }
    public List<Sku> publicSkus(long productId){
        detail(productId,false);
        return cache.remember("product:sku:"+productId,SkuList.class,Duration.ofMinutes(30),
            ()->new SkuList(skus.selectList(new QueryWrapper<Sku>().eq("product_id",productId).eq("status","ACTIVE").orderByAsc("id")))).skus();
    }
    public PageResult<Product> list(boolean admin,int page,int size,String keyword,Long categoryId,BigDecimal min,BigDecimal max,String sort){
        Db.check(min==null||min.signum()>=0,"最低价格不能为负");Db.check(max==null||max.signum()>=0,"最高价格不能为负");Db.check(min==null||max==null||min.compareTo(max)<=0,"价格区间无效");
        QueryWrapper<Product> q=new QueryWrapper<>();
        if(!admin){q.eq("status","ACTIVE");List<Long> ids=categories(false).stream().map(c->c.id).toList();if(ids.isEmpty())q.eq("category_id",-1);else q.in("category_id",ids);
            q.exists("SELECT 1 FROM product_skus s WHERE s.product_id=products.id AND s.status='ACTIVE'");}
        if(keyword!=null&&!keyword.isBlank())q.like("name",keyword);if(categoryId!=null)q.eq("category_id",categoryId);
        String skuQuery="SELECT 1 FROM product_skus s WHERE s.product_id=products.id AND s.status='ACTIVE'";
        if(min!=null&&max!=null)q.exists(skuQuery+" AND s.price >= {0} AND s.price <= {1}",min,max);
        else if(min!=null)q.exists(skuQuery+" AND s.price >= {0}",min);
        else if(max!=null)q.exists(skuQuery+" AND s.price <= {0}",max);
        switch(sort==null?"newest":sort){case "newest"->q.orderByDesc("id");case "oldest"->q.orderByAsc("id");case "name"->q.orderByAsc("name","id");
            case "priceAsc","priceDesc"->q.last("ORDER BY (SELECT MIN(s.price) FROM product_skus s WHERE s.product_id=products.id AND s.status='ACTIVE') "+("priceAsc".equals(sort)?"ASC":"DESC")+" NULLS LAST, id DESC");
            default->throw ApiException.conflict("sort 支持 newest、oldest、name、priceAsc、priceDesc");}
        return PageResult.of(products,page,size,q);
    }
    @Transactional public Product product(Actor actor,Long id,CatalogDtos.ProductInput in){
        Db.require(categories.selectById(in.categoryId()));Product p=id==null?new Product():Db.lock(products,id);Object before=id==null?null:JsonbHandler.object(p);
        p.categoryId=in.categoryId();p.name=in.name();p.description=in.description();p.brand=in.brand();p.imageUrl=in.imageUrl();p.updatedAt=Db.now();
        if(id==null){p.status="DRAFT";products.insert(p);}else products.updateById(p);
        invalidate(p.id);audit.record(actor,"SAVE_PRODUCT","PRODUCT",p.id,before,p);return products.selectById(p.id);
    }
    @Transactional public void productStatus(Actor actor,long id,boolean publish){
        Product p=Db.lock(products,id);var before=JsonbHandler.object(p);
        if(publish){Db.check(categoryActive(p.categoryId),"分类未启用");Db.check(skus.selectCount(new QueryWrapper<Sku>().eq("product_id",id).eq("status","ACTIVE"))>0,"至少需要一个启用的 SKU");}
        p.status=publish?"ACTIVE":"INACTIVE";p.updatedAt=Db.now();products.updateById(p);invalidate(id);audit.record(actor,publish?"PUBLISH_PRODUCT":"UNPUBLISH_PRODUCT","PRODUCT",id,before,p);
    }
    @Transactional public Sku sku(Actor actor,long productId,Long id,CatalogDtos.SkuInput in){
        Db.lock(products,productId);Sku s=id==null?new Sku():Db.lock(skus,id);if(id!=null)Db.check(s.productId==productId,"SKU 不属于商品");
        Db.check(JsonbHandler.encode(in.attributes()).length()<=4000,"SKU 属性过长");Object before=id==null?null:JsonbHandler.object(s);
        s.productId=productId;s.skuCode=in.skuCode();s.attributes=in.attributes();s.price=in.price();s.status=in.status();s.updatedAt=Db.now();
        if(id==null){skus.insert(s);inventory.initialize(actor,s.id,in.initialStock());}else skus.updateById(s);
        invalidate(productId);audit.record(actor,"SAVE_SKU","SKU",s.id,before,s);return skus.selectById(s.id);
    }
    @Transactional public void deleteSku(Actor actor,long id){
        Sku old=Db.require(skus.selectById(id));Db.lock(products,old.productId);Sku s=Db.lock(skus,id);var before=JsonbHandler.object(s);s.status="INACTIVE";s.updatedAt=Db.now();skus.updateById(s);
        invalidate(s.productId);audit.record(actor,"DISABLE_SKU","SKU",id,before,s);
    }
    public void invalidate(long id){cache.evict("product:"+id,"product:sku:"+id,"dashboard:overview");}
}
