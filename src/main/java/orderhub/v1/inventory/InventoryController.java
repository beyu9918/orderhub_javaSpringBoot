package orderhub.v1.inventory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import orderhub.v1.common.*;
import orderhub.v1.inventory.entity.*;
import orderhub.v1.inventory.mapper.*;
import orderhub.v1.product.CatalogDtos.Adjustment;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin/inventory") @Tag(name="V1 库存管理")
public class InventoryController {
    private final InventoryService inventory;private final StockMapper stocks;private final StockLogMapper logs;
    public InventoryController(InventoryService inventory,StockMapper stocks,StockLogMapper logs){this.inventory=inventory;this.stocks=stocks;this.logs=logs;}
    @GetMapping public PageResult<Stock> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return PageResult.of(stocks,page,size,new QueryWrapper<Stock>().orderByAsc("sku_id"));}
    @GetMapping("/low-stock") public PageResult<Stock> low(@RequestParam(defaultValue="10") int threshold,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){Db.check(threshold>=0,"阈值不能为负");return PageResult.of(stocks,page,size,new QueryWrapper<Stock>().le("available_stock",threshold).orderByAsc("available_stock","sku_id"));}
    @GetMapping("/{skuId}") public Stock detail(@PathVariable long skuId){return inventory.get(skuId);}
    @PostMapping("/{skuId}/adjust") public Stock adjust(@PathVariable long skuId,@Valid @RequestBody Adjustment in){return inventory.adjust(Actor.current(),skuId,in);}
    @GetMapping("/{skuId}/logs") public PageResult<StockLog> logs(@PathVariable long skuId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return PageResult.of(logs,page,size,new QueryWrapper<StockLog>().eq("sku_id",skuId).orderByDesc("created_at","id"));}
}
