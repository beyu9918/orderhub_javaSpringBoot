package orderhub.v1.product;

import java.math.BigDecimal;
import java.util.List;
import orderhub.v1.category.entity.Category;
import orderhub.v1.common.PageResult;
import orderhub.v1.product.entity.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController @RequestMapping("/api/v1/user") @Tag(name="V1 商品浏览") @SecurityRequirements
public class ProductController {
    private final CatalogService catalog;public ProductController(CatalogService catalog){this.catalog=catalog;}
    @GetMapping("/categories") public List<Category> categories(){return catalog.categories(false);}
    @GetMapping("/products") public PageResult<Product> products(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,
        @RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,@RequestParam(defaultValue="newest") String sort){return catalog.list(false,page,size,keyword,categoryId,minPrice,maxPrice,sort);}
    @GetMapping("/products/{id}") public CatalogService.ProductDetail detail(@PathVariable long id){return catalog.detail(id,false);}
    @GetMapping("/products/{id}/skus") public List<Sku> skus(@PathVariable long id){return catalog.publicSkus(id);}
}
