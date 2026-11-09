package orderhub.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import orderhub.api.Views.*;
import orderhub.service.ProductService;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="orderhub.legacy-api.enabled",havingValue="true")
@RequestMapping("/api/products")
@Tag(name = "商品")
@SecurityRequirements
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }

    @GetMapping @Operation(summary = "分页查询上架商品，支持分类和名称关键字")
    public PageView<ProductView> list(@RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                     @RequestParam(required = false) @Size(max = 100) String category,
                                     @RequestParam(required = false) @Size(max = 100) String keyword) {
        return products.list(page, size, category, keyword, false);
    }

    @GetMapping("/{id}") @Operation(summary = "商品详情，Redis 缓存 60 秒")
    public ProductView detail(@PathVariable @Positive long id) { return products.detail(id); }
}
