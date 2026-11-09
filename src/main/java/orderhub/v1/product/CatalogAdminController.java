package orderhub.v1.product;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import orderhub.v1.category.entity.Category;
import orderhub.v1.common.*;
import orderhub.v1.product.entity.*;
import orderhub.v1.product.mapper.SkuMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin") @Tag(name="V1 分类商品与 SKU 管理")
public class CatalogAdminController {
    private final CatalogService catalog;private final SkuMapper skus;
    public CatalogAdminController(CatalogService catalog,SkuMapper skus){this.catalog=catalog;this.skus=skus;}
    @GetMapping("/categories") public List<Category> categories(){return catalog.categories(true);}
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) public Category addCategory(@Valid @RequestBody CatalogDtos.CategoryInput in){return catalog.category(Actor.current(),null,in);}
    @PutMapping("/categories/{id}") public Category editCategory(@PathVariable long id,@Valid @RequestBody CatalogDtos.CategoryInput in){return catalog.category(Actor.current(),id,in);}
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCategory(@PathVariable long id){catalog.deleteCategory(Actor.current(),id);}
    @PutMapping("/categories/{id}/enable") @ResponseStatus(HttpStatus.NO_CONTENT) public void enableCategory(@PathVariable long id){catalog.categoryStatus(Actor.current(),id,true);}
    @PutMapping("/categories/{id}/disable") @ResponseStatus(HttpStatus.NO_CONTENT) public void disableCategory(@PathVariable long id){catalog.categoryStatus(Actor.current(),id,false);}
    @GetMapping("/products") public PageResult<Product> products(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,
        @RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,@RequestParam(defaultValue="newest") String sort){return catalog.list(true,page,size,keyword,categoryId,minPrice,maxPrice,sort);}
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) public Product create(@Valid @RequestBody CatalogDtos.ProductInput in){return catalog.product(Actor.current(),null,in);}
    @GetMapping("/products/{id}") public CatalogService.ProductDetail detail(@PathVariable long id){return catalog.detail(id,true);}
    @PutMapping("/products/{id}") public Product update(@PathVariable long id,@Valid @RequestBody CatalogDtos.ProductInput in){return catalog.product(Actor.current(),id,in);}
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){catalog.productStatus(Actor.current(),id,false);}
    @PutMapping("/products/{id}/publish") @ResponseStatus(HttpStatus.NO_CONTENT) public void publish(@PathVariable long id){catalog.productStatus(Actor.current(),id,true);}
    @PutMapping("/products/{id}/unpublish") @ResponseStatus(HttpStatus.NO_CONTENT) public void unpublish(@PathVariable long id){catalog.productStatus(Actor.current(),id,false);}
    @GetMapping("/products/{productId}/skus") public List<Sku> skus(@PathVariable long productId){return catalog.detail(productId,true).skus();}
    @PostMapping("/products/{productId}/skus") @ResponseStatus(HttpStatus.CREATED) public Sku createSku(@PathVariable long productId,@Valid @RequestBody CatalogDtos.SkuInput in){return catalog.sku(Actor.current(),productId,null,in);}
    @PutMapping("/skus/{skuId}") public Sku updateSku(@PathVariable long skuId,@Valid @RequestBody CatalogDtos.SkuInput in){return catalog.sku(Actor.current(),Db.require(skus.selectById(skuId)).productId,skuId,in);}
    @DeleteMapping("/skus/{skuId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSku(@PathVariable long skuId){catalog.deleteSku(Actor.current(),skuId);}
}
