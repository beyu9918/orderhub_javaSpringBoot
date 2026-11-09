package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;

/** One-time copy. The original demo tables remain available to the old /api endpoints. */
public class V3__Import_legacy_data extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        execute(c,"INSERT INTO users(id,email,username,password_hash,role,created_at,updated_at) " +
                "SELECT id,'legacy-' || CAST(id AS VARCHAR) || '@legacy.invalid',username,password_hash,role,created_at,created_at FROM app_user");
        execute(c,"INSERT INTO carts(id,user_id) SELECT id,id FROM users");
        Map<String,Long> categories = new HashMap<>();
        try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT DISTINCT category FROM product")) {
            while(rs.next()) {
                String name=rs.getString(1);
                try (PreparedStatement p=c.prepareStatement("INSERT INTO categories(name) VALUES(?)",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,name); p.executeUpdate();
                    try (ResultSet keys=p.getGeneratedKeys()) { keys.next(); categories.put(name,keys.getLong(1)); }
                }
            }
        }
        try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT * FROM product")) {
            while(rs.next()) {
                try (PreparedStatement p=c.prepareStatement("INSERT INTO products(id,category_id,name,description,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                    p.setLong(1,rs.getLong("id")); p.setLong(2,categories.get(rs.getString("category")));
                    p.setString(3,rs.getString("name")); p.setString(4,rs.getString("description"));
                    p.setString(5,rs.getBoolean("active")?"ACTIVE":"INACTIVE");
                    p.setObject(6,rs.getObject("created_at")); p.setObject(7,rs.getObject("created_at")); p.executeUpdate();
                }
            }
        }
        execute(c,"INSERT INTO product_skus(id,product_id,sku_code,attributes,price,status,created_at,updated_at) " +
                "SELECT id,id,sku,CAST('{}' AS JSONB),price,CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END,created_at,created_at FROM product");
        execute(c,"INSERT INTO inventory(sku_id,available_stock,reserved_stock) SELECT i.product_id,i.quantity," +
                "COALESCE((SELECT SUM(oi.quantity) FROM order_item oi JOIN shop_order o ON o.id=oi.order_id " +
                "WHERE oi.product_id=i.product_id AND o.status='CREATED'),0) FROM legacy_inventory i");
        execute(c,"INSERT INTO cart_items(id,cart_id,sku_id,quantity) SELECT id,user_id,product_id,quantity FROM cart_item");
        execute(c,"INSERT INTO orders(id,order_no,user_id,address_snapshot,request_key,request_hash,status,subtotal_amount,payable_amount,created_at,updated_at) " +
                "SELECT id,order_no,user_id,CAST('{\"legacy\":true}' AS JSONB),request_key,'legacy'," +
                "CASE WHEN status='CREATED' THEN 'PENDING_PAYMENT' ELSE 'CANCELLED' END,total_amount,total_amount,created_at,created_at FROM shop_order");
        execute(c,"INSERT INTO order_items(id,order_id,product_id,sku_id,product_name,sku_attributes,unit_price,quantity,subtotal) " +
                "SELECT id,order_id,product_id,product_id,product_name,CAST('{}' AS JSONB),unit_price,quantity,subtotal FROM order_item");
        execute(c,"INSERT INTO order_status_logs(order_id,to_status,operator_type,reason) SELECT id,status,'SYSTEM','Imported from original demo' FROM orders");
        execute(c,"INSERT INTO inventory_logs(sku_id,change_type,quantity,before_stock,after_stock,before_reserved,after_reserved,remark) " +
                "SELECT sku_id,'PURCHASE',available_stock,0,available_stock,0,reserved_stock,'Imported current balances' FROM inventory");
        for(String table:List.of("users","addresses","categories","products","product_skus","inventory","inventory_logs","carts","cart_items","orders","order_items","order_status_logs","payments","shipments","refunds","coupons","user_coupons","audit_logs")) {
            long next;
            try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM "+table)) { rs.next(); next=rs.getLong(1); }
            execute(c,"ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH "+next);
        }
    }
    private void execute(Connection c,String sql) throws SQLException { try(Statement s=c.createStatement()) { s.executeUpdate(sql); } }
}
