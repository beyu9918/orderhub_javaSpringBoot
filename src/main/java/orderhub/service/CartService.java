package orderhub.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import orderhub.api.ApiException;
import orderhub.api.Views.CartView;
import orderhub.domain.Entities.*;
import orderhub.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final UserMapper users;
    private final CartMapper cart;
    private final ProductMapper products;

    public CartService(UserMapper users, CartMapper cart, ProductMapper products) {
        this.users = users; this.cart = cart; this.products = products;
    }

    public List<CartView> list(long userId) {
        List<CartItem> items = cart.selectList(new QueryWrapper<CartItem>().eq("user_id", userId).orderByAsc("product_id"));
        if (items.isEmpty()) return List.of();
        Map<Long, Product> byId = products.selectByIds(items.stream().map(i -> i.productId).toList())
                .stream().collect(Collectors.toMap(p -> p.id, p -> p));
        return items.stream().map(i -> {
            Product p = byId.get(i.productId);
            return new CartView(i.productId, p.name, p.price, i.quantity, p.active);
        }).toList();
    }

    @Transactional
    public void put(long userId, long productId, int quantity) {
        lockUser(userId);
        Product p = products.selectById(productId);
        if (p == null || !p.active) throw ApiException.missing("商品不存在或已下架");
        var query = new QueryWrapper<CartItem>().eq("user_id", userId).eq("product_id", productId);
        CartItem item = cart.selectOne(query);
        if (item == null) {
            if (cart.selectCount(new QueryWrapper<CartItem>().eq("user_id", userId)) >= 100)
                throw ApiException.conflict("购物车最多支持 100 种商品");
            item = new CartItem();
            item.userId = userId; item.productId = productId; item.quantity = quantity;
            cart.insert(item);
        } else {
            item.quantity = quantity;
            cart.updateById(item);
        }
    }

    @Transactional
    public void remove(long userId, long productId) {
        lockUser(userId);
        cart.delete(new QueryWrapper<CartItem>().eq("user_id", userId).eq("product_id", productId));
    }

    @Transactional
    public void clear(long userId) {
        lockUser(userId);
        cart.delete(new QueryWrapper<CartItem>().eq("user_id", userId));
    }

    private void lockUser(long userId) {
        if (users.lockById(userId) == null) throw ApiException.missing("用户不存在");
    }
}
