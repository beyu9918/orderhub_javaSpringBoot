package orderhub.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.util.*;
import orderhub.api.ApiException;
import orderhub.api.Views.*;
import orderhub.domain.Entities.*;
import orderhub.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final UserMapper users;
    private final CartMapper cart;
    private final ProductMapper products;
    private final InventoryMapper inventory;
    private final OrderMapper orders;
    private final OrderItemMapper lines;

    public OrderService(UserMapper users, CartMapper cart, ProductMapper products, InventoryMapper inventory,
                        OrderMapper orders, OrderItemMapper lines) {
        this.users = users; this.cart = cart; this.products = products;
        this.inventory = inventory; this.orders = orders; this.lines = lines;
    }

    @Transactional
    public OrderView checkout(long userId, String requestKey) {
        if (users.lockById(userId) == null) throw ApiException.missing("用户不存在");
        Order existing = orders.selectOne(new QueryWrapper<Order>().eq("user_id", userId).eq("request_key", requestKey));
        if (existing != null) return view(existing);
        List<CartItem> items = cart.selectList(new QueryWrapper<CartItem>().eq("user_id", userId).orderByAsc("product_id"));
        if (items.isEmpty()) throw ApiException.conflict("购物车为空");

        List<OrderItem> snapshots = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product p = products.lockById(item.productId);
            if (p == null || !p.active) throw ApiException.conflict("购物车包含已下架商品");
            if (inventory.deduct(p.id, item.quantity) != 1) throw ApiException.conflict("商品库存不足：" + p.name);
            var line = new OrderItem();
            line.productId = p.id; line.productName = p.name; line.unitPrice = p.price;
            line.quantity = item.quantity; line.subtotal = p.price.multiply(BigDecimal.valueOf(item.quantity));
            snapshots.add(line);
            total = total.add(line.subtotal);
        }
        var order = new Order();
        order.orderNo = UUID.randomUUID().toString(); order.userId = userId; order.requestKey = requestKey;
        order.status = "CREATED"; order.totalAmount = total;
        orders.insert(order);
        for (OrderItem line : snapshots) { line.orderId = order.id; lines.insert(line); }
        cart.delete(new QueryWrapper<CartItem>().eq("user_id", userId));
        return view(orders.selectById(order.id));
    }

    public PageView<OrderSummary> history(long userId, int page, int size, String status) {
        var query = new QueryWrapper<Order>().eq("user_id", userId);
        if (status != null) query.eq("status", status);
        query.orderByDesc("created_at", "id");
        return PageView.of(orders.selectPage(new Page<>(page, size), query).convert(OrderSummary::of));
    }

    public OrderView detail(long userId, long id) {
        Order order = orders.selectOne(new QueryWrapper<Order>().eq("id", id).eq("user_id", userId));
        if (order == null) throw ApiException.missing("订单不存在");
        return view(order);
    }

    @Transactional
    public OrderView cancel(long userId, long id) {
        Order order = orders.lockOwned(id, userId);
        if (order == null) throw ApiException.missing("订单不存在");
        if ("CANCELLED".equals(order.status)) return view(order);
        List<OrderItem> items = orderLines(order.id);
        for (OrderItem item : items) {
            products.lockById(item.productId);
            inventory.restore(item.productId, item.quantity);
        }
        order.status = "CANCELLED";
        orders.updateById(order);
        return view(order);
    }

    private List<OrderItem> orderLines(long id) {
        return lines.selectList(new QueryWrapper<OrderItem>().eq("order_id", id).orderByAsc("product_id"));
    }

    private OrderView view(Order order) {
        return new OrderView(order.id, order.orderNo, order.status, order.totalAmount, order.createdAt,
                orderLines(order.id).stream().map(OrderLine::of).toList());
    }
}
