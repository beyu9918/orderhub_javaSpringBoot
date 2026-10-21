package orderhub.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import orderhub.domain.Entities.*;

public final class Views {
    private Views() {}

    public record Token(String accessToken, String tokenType, long expiresIn) {}
    public record UserView(long id, String username, String role) {}
    public record ProductView(long id, String sku, String name, String description, String category,
                              BigDecimal price, boolean active, OffsetDateTime createdAt) {
        public static ProductView of(Product p) {
            return new ProductView(p.id, p.sku, p.name, p.description, p.category, p.price, p.active, p.createdAt);
        }
    }
    public record StockView(long productId, int quantity) {}
    public record CartView(long productId, String name, BigDecimal unitPrice, int quantity, boolean active) {}
    public record OrderView(long id, String orderNo, String status, BigDecimal totalAmount,
                            OffsetDateTime createdAt, List<OrderLine> items) {}
    public record OrderLine(long productId, String productName, BigDecimal unitPrice, int quantity,
                            BigDecimal subtotal) {
        public static OrderLine of(OrderItem i) {
            return new OrderLine(i.productId, i.productName, i.unitPrice, i.quantity, i.subtotal);
        }
    }
    public record OrderSummary(long id, String orderNo, String status, BigDecimal totalAmount,
                               OffsetDateTime createdAt) {
        public static OrderSummary of(Order o) {
            return new OrderSummary(o.id, o.orderNo, o.status, o.totalAmount, o.createdAt);
        }
    }
    public record PageView<T>(long page, long size, long total, List<T> records) {
        public static <T> PageView<T> of(IPage<T> page) {
            return new PageView<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
        }
    }
}
