package orderhub.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import orderhub.api.Requests.*;
import orderhub.api.Views.*;
import orderhub.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="orderhub.legacy-api.enabled",havingValue="true")
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "商品与库存管理（管理员）")
public class AdminController {
    private final ProductService products;
    public AdminController(ProductService products) { this.products = products; }

    @GetMapping @Operation(summary = "分页查询全部商品，包含下架商品")
    public PageView<ProductView> list(@RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                     @RequestParam(required = false) @Size(max = 100) String category,
                                     @RequestParam(required = false) @Size(max = 100) String keyword) {
        return products.list(page, size, category, keyword, true);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "创建商品及初始库存")
    public ProductView create(@Valid @RequestBody ProductInput input) { return products.create(input); }

    @PutMapping("/{id}") @Operation(summary = "更新商品及库存")
    public ProductView update(@PathVariable @Positive long id, @Valid @RequestBody ProductInput input) {
        return products.update(id, input);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "下架商品，保留历史订单关联")
    public void deactivate(@PathVariable @Positive long id) { products.deactivate(id); }

    @GetMapping("/{id}/stock") @Operation(summary = "实时库存")
    public StockView stock(@PathVariable @Positive long id) { return products.stock(id); }

    @PutMapping("/{id}/stock") @Operation(summary = "设置库存数量")
    public StockView setStock(@PathVariable @Positive long id, @Valid @RequestBody StockInput input) {
        return products.setStock(id, input.quantity());
    }
}
