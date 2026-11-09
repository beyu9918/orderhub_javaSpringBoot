package orderhub.v1.report;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import orderhub.v1.common.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;private final CommerceCache cache;
    public ReportService(JdbcTemplate jdbc,CommerceCache cache){this.jdbc=jdbc;this.cache=cache;}
    public record Overview(BigDecimal todayRevenue,long todayOrders,long paidOrders,long refundOrders,long newUsers,long pendingShipmentOrders){}
    public Overview overview(){return cache.remember("dashboard:overview",Overview.class,Duration.ofMinutes(1),()->{
        var today=LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);var tomorrow=today.plusDays(1);
        return new Overview(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS' AND paid_at>=? AND paid_at<?",BigDecimal.class,today,tomorrow),
            count("SELECT COUNT(*) FROM orders WHERE created_at>=? AND created_at<?",today,tomorrow),
            count("SELECT COUNT(DISTINCT order_id) FROM payments WHERE status IN ('SUCCESS','REFUNDED')"),
            count("SELECT COUNT(*) FROM orders WHERE status IN ('REFUND_REQUESTED','REFUNDED')"),
            count("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?",today,tomorrow),
            count("SELECT COUNT(*) FROM orders WHERE status IN ('PAID','PROCESSING')"));
    });}
    private long count(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private OffsetDateTime[] range(LocalDate start,LocalDate end){
        LocalDate actualEnd=end==null?LocalDate.now(ZoneOffset.UTC):end;LocalDate actualStart=start==null?actualEnd.minusDays(6):start;
        Db.check(!actualStart.isAfter(actualEnd)&&!actualStart.isBefore(actualEnd.minusDays(365)),"日期范围必须有序，且最多 366 天");
        return new OffsetDateTime[]{actualStart.atStartOfDay().atOffset(ZoneOffset.UTC),actualEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)};
    }
    public List<Map<String,Object>> sales(LocalDate start,LocalDate end){var r=range(start,end);return jdbc.queryForList(
        "SELECT CAST(paid_at AT TIME ZONE 'UTC' AS DATE) AS day, COUNT(*) AS paid_orders, SUM(amount) AS revenue FROM payments " +
        "WHERE status='SUCCESS' AND paid_at>=? AND paid_at<? GROUP BY CAST(paid_at AT TIME ZONE 'UTC' AS DATE) ORDER BY day",r[0],r[1]);}
    public List<Map<String,Object>> orderTrend(LocalDate start,LocalDate end){var r=range(start,end);return jdbc.queryForList(
        "SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, COUNT(*) AS orders FROM orders WHERE created_at>=? AND created_at<? " +
        "GROUP BY CAST(created_at AT TIME ZONE 'UTC' AS DATE) ORDER BY day",r[0],r[1]);}
    public List<Map<String,Object>> statuses(){return jdbc.queryForList("SELECT status,COUNT(*) AS orders FROM orders GROUP BY status ORDER BY status");}
    public List<Map<String,Object>> products(LocalDate start,LocalDate end,int limit){
        Db.check(limit>=1&&limit<=100,"limit 为 1–100");var r=range(start,end);return jdbc.queryForList(
        "SELECT i.sku_id, MAX(i.product_name) AS product_name, SUM(i.quantity) AS quantity, SUM(i.subtotal) AS gross_item_amount " +
        "FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.paid_at>=? AND o.paid_at<? " +
        "AND o.status IN ('PAID','PROCESSING','SHIPPED','DELIVERED','COMPLETED','REFUND_REQUESTED') " +
        "GROUP BY i.sku_id ORDER BY quantity DESC,i.sku_id LIMIT ?",r[0],r[1],limit);
    }
    public List<Map<String,Object>> orders(LocalDate start,LocalDate end){var r=range(start,end);return jdbc.queryForList(
        "SELECT id,order_no,user_id,status,subtotal_amount,discount_amount,shipping_amount,payable_amount,created_at,paid_at FROM orders " +
        "WHERE created_at>=? AND created_at<? ORDER BY created_at DESC,id DESC LIMIT 10001",r[0],r[1]);}
    public List<Map<String,Object>> low(int threshold){Db.check(threshold>=0,"阈值不能为负");return jdbc.queryForList(
        "SELECT i.sku_id,s.sku_code,p.name,i.available_stock,i.reserved_stock,i.version FROM inventory i " +
        "JOIN product_skus s ON s.id=i.sku_id JOIN products p ON p.id=s.product_id WHERE i.available_stock<=? ORDER BY i.available_stock,i.sku_id LIMIT 100",threshold);}
    public byte[] excel(LocalDate start,LocalDate end){
        List<Map<String,Object>> rows=orders(start,end);Db.check(rows.size()<=10000,"导出最多 10000 行，请缩小日期范围");
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=workbook.createSheet("Orders");List<String> columns=List.of("id","order_no","user_id","status","subtotal_amount","discount_amount","shipping_amount","payable_amount","created_at","paid_at");
            var header=sheet.createRow(0);for(int i=0;i<columns.size();i++){header.createCell(i).setCellValue(columns.get(i));sheet.setColumnWidth(i,24*256);}
            int rowId=1;for(Map<String,Object> row:rows){var target=sheet.createRow(rowId++);for(int i=0;i<columns.size();i++){Object value=row.get(columns.get(i));target.createCell(i).setCellValue(value==null?"":value instanceof BigDecimal b?b.toPlainString():value.toString());}}
            sheet.createFreezePane(0,1);workbook.write(output);return output.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Cannot write report",e);}
    }
}
