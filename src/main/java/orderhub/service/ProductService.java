package orderhub.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import orderhub.api.*;
import orderhub.api.Requests.ProductInput;
import orderhub.api.Views.*;
import orderhub.domain.Entities.*;
import orderhub.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductMapper products;
    private final InventoryMapper inventory;
    private final ProductCache cache;

    public ProductService(ProductMapper products, InventoryMapper inventory, ProductCache cache) {
        this.products = products;
        this.inventory = inventory;
        this.cache = cache;
    }

    public PageView<ProductView> list(int page, int size, String category, String keyword, boolean admin) {
        var query = new QueryWrapper<Product>();
        if (!admin) query.eq("active", true);
        if (category != null && !category.isBlank()) query.eq("category", category);
        if (keyword != null && !keyword.isBlank()) query.like("name", keyword);
        query.orderByDesc("id");
        return PageView.of(products.selectPage(new Page<>(page, size), query).convert(ProductView::of));
    }

    public ProductView detail(long id) {
        return cache.get(id).filter(ProductView::active).orElseGet(() -> {
            Product p = products.selectById(id);
            if (p == null || !p.active) throw ApiException.missing("商品不存在或已下架");
            var view = ProductView.of(p);
            cache.put(view);
            return view;
        });
    }

    @Transactional
    public ProductView create(ProductInput input) {
        Product p = new Product();
        apply(p, input);
        products.insert(p);
        Inventory stock = new Inventory();
        stock.productId = p.id;
        stock.quantity = input.stock();
        inventory.insert(stock);
        return ProductView.of(products.selectById(p.id));
    }

    @Transactional
    public ProductView update(long id, ProductInput input) {
        Product p = locked(id);
        apply(p, input);
        products.updateById(p);
        setStockLocked(id, input.stock());
        cache.evictAfterCommit(id);
        return ProductView.of(p);
    }

    @Transactional
    public void deactivate(long id) {
        Product p = locked(id);
        p.active = false;
        products.updateById(p);
        cache.evictAfterCommit(id);
    }

    public StockView stock(long id) {
        Inventory stock = inventory.selectById(id);
        if (stock == null) throw ApiException.missing("商品不存在");
        return new StockView(id, stock.quantity);
    }

    @Transactional
    public StockView setStock(long id, int quantity) {
        locked(id);
        setStockLocked(id, quantity);
        return new StockView(id, quantity);
    }

    private void setStockLocked(long id, int quantity) {
        Inventory stock = new Inventory();
        stock.productId = id;
        stock.quantity = quantity;
        stock.updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        inventory.updateById(stock);
    }

    private Product locked(long id) {
        Product p = products.lockById(id);
        if (p == null) throw ApiException.missing("商品不存在");
        return p;
    }

    private void apply(Product p, ProductInput i) {
        p.sku = i.sku(); p.name = i.name(); p.description = i.description();
        p.category = i.category(); p.price = i.price(); p.active = i.active();
    }
}
