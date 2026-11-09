package orderhub.v1.report;

import java.time.LocalDate;
import java.util.*;
import orderhub.v1.audit.AuditService;
import orderhub.v1.common.Actor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin") @Tag(name="V1 Dashboard 与 Excel 报表")
public class ReportController {
    private final ReportService reports;private final AuditService audit;
    public ReportController(ReportService reports,AuditService audit){this.reports=reports;this.audit=audit;}
    @GetMapping("/dashboard/overview") public ReportService.Overview overview(){return reports.overview();}
    @GetMapping({"/dashboard/sales-trend","/reports/sales"}) public List<Map<String,Object>> sales(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate){return reports.sales(startDate,endDate);}
    @GetMapping("/dashboard/order-trend") public List<Map<String,Object>> trend(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate){return reports.orderTrend(startDate,endDate);}
    @GetMapping("/dashboard/order-status") public List<Map<String,Object>> statuses(){return reports.statuses();}
    @GetMapping({"/dashboard/top-products","/reports/products"}) public List<Map<String,Object>> products(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,@RequestParam(defaultValue="10") int limit){return reports.products(startDate,endDate,limit);}
    @GetMapping("/dashboard/low-stock") public List<Map<String,Object>> low(@RequestParam(defaultValue="10") int threshold){return reports.low(threshold);}
    @GetMapping("/reports/orders") public List<Map<String,Object>> orders(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate){return reports.orders(startDate,endDate);}
    @GetMapping("/reports/export") public ResponseEntity<byte[]> export(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate){
        byte[] bytes=reports.excel(startDate,endDate);audit.record(Actor.current(),"EXPORT_REPORT","REPORT",null,null,Map.of("bytes",bytes.length));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=orderhub-orders.xlsx").body(bytes);
    }
}
