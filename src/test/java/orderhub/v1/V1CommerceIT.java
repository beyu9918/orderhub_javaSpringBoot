package orderhub.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import orderhub.api.ApiException;
import orderhub.v1.address.*;
import orderhub.v1.auth.*;
import orderhub.v1.cart.*;
import orderhub.v1.category.mapper.CategoryMapper;
import orderhub.v1.common.*;
import orderhub.v1.coupon.*;
import orderhub.v1.coupon.entity.*;
import orderhub.v1.coupon.mapper.*;
import orderhub.v1.inventory.InventoryService;
import orderhub.v1.order.*;
import orderhub.v1.order.entity.PurchaseOrder;
import orderhub.v1.order.mapper.PurchaseOrderMapper;
import orderhub.v1.product.*;
import orderhub.v1.report.ReportService;
import orderhub.v1.task.OrderTasks;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "orderhub.jwt.secret=v1-integration-test-secret-at-least-32-bytes","orderhub.cache.enabled=true",
    "orderhub.admin.username=legacyadmin","orderhub.admin.password=Legacy-Test-123!",
    "orderhub.v1.enabled=false","orderhub.v1.tasks.enabled=false","orderhub.v1.files-directory=.local/test-images","debug=false","logging.level.root=WARN"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="V1_TEST_DB_URL",matches="jdbc:postgresql:.*_test(?:\\?.*)?")
class V1CommerceIT {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->System.getenv("V1_TEST_DB_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("V1_TEST_DB_USERNAME","orderhub"));
        p.add("spring.datasource.password",()->System.getenv("V1_TEST_DB_PASSWORD"));
        p.add("spring.data.redis.host",()->"localhost");p.add("spring.data.redis.port",()->System.getenv().getOrDefault("V1_TEST_REDIS_PORT","16379"));
        p.add("spring.data.redis.database",()->1);
    }
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired JdbcTemplate jdbc;@Autowired DataSource dataSource;
    @Autowired AuthService auth;@Autowired AddressService addresses;@Autowired CatalogService catalog;@Autowired InventoryService inventory;
    @Autowired CartService carts;@Autowired OrderService orders;@Autowired CouponService coupons;@Autowired ReportService reports;
    @Autowired PurchaseOrderMapper orderRows;@Autowired UserCouponMapper userCoupons;@Autowired StringRedisTemplate redis;
    @Autowired ApplicationEventPublisher events;@Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    Actor admin;String adminToken;
    record Shopper(Actor actor,String token,long address){}
    @BeforeEach void reset(){
        assertThat(System.getenv("V1_TEST_DB_URL")).contains("_test");
        jdbc.execute("TRUNCATE users,categories,coupons,audit_logs RESTART IDENTITY CASCADE");
        var a=auth.register(new AuthDtos.Register("admin@test.local","admin","Admin-Test-123!",null),"ADMIN",Actor.system());admin=new Actor(a.id,"ADMIN");
        adminToken=auth.login(new AuthDtos.Login("admin","Admin-Test-123!"),true).accessToken();
        Set<String> keys=redis.keys("product:*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);
        keys=redis.keys("order:idempotency:*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);redis.delete("dashboard:overview");
    }
    @Test void completePurchaseAndAuthenticatedWebSocket() throws Exception {
        Shopper u=shopper("alice");long sku=sku("49.90",10);carts.add(u.actor.id(),sku,2);
        long order=api("POST","/api/v1/user/orders",u.token,new OrderDtos.Checkout(u.address,null,"test"),201,"purchase-key-001").at("/order/id").asLong();
        assertThat(inventory.get(sku).availableStock).isEqualTo(8);assertThat(inventory.get(sku).reservedStock).isEqualTo(2);
        SocketListener listener=new SocketListener(),adminListener=new SocketListener();
        var client=HttpClient.newHttpClient();
        WebSocket socket=client.newWebSocketBuilder().header("Authorization","Bearer "+u.token).buildAsync(URI.create("ws://localhost:"+port+"/ws/orders"),listener).join();
        WebSocket adminSocket=client.newWebSocketBuilder().header("Authorization","Bearer "+adminToken).buildAsync(URI.create("ws://localhost:"+port+"/ws/admin/orders"),adminListener).join();
        assertThatThrownBy(()->client.newWebSocketBuilder().header("Authorization","Bearer "+u.token).buildAsync(URI.create("ws://localhost:"+port+"/ws/admin/orders"),new SocketListener()).join()).isInstanceOf(CompletionException.class);
        long first=api("POST","/api/v1/user/orders/"+order+"/payments",u.token,Map.of("method","MOCK"),201,null).get("id").asLong();
        api("POST","/api/v1/user/payments/"+first+"/mock-fail",u.token,null,200,null);
        long payment=api("POST","/api/v1/user/orders/"+order+"/payments",u.token,Map.of("method","MOCK"),201,null).get("id").asLong();
        assertThat(payment).isNotEqualTo(first);
        api("POST","/api/v1/user/payments/"+payment+"/mock-success",u.token,null,200,null);
        api("POST","/api/v1/user/payments/"+payment+"/mock-success",u.token,null,200,null);
        assertThat(listener.messages.poll(5,TimeUnit.SECONDS)).contains("ORDER_PAID");assertThat(adminListener.messages.poll(5,TimeUnit.SECONDS)).contains("ORDER_PAID");
        api("POST","/api/v1/admin/orders/"+order+"/process",adminToken,null,200,null);
        long shipment=api("POST","/api/v1/admin/orders/"+order+"/ship",adminToken,new OrderDtos.Ship("MOCK","TRACK-001"),200,null).get("id").asLong();
        long secondShipment=api("POST","/api/v1/admin/orders/"+order+"/shipments",adminToken,new OrderDtos.Ship("MOCK","TRACK-002"),200,null).get("id").asLong();
        api("PUT","/api/v1/admin/shipments/"+shipment+"/status",adminToken,Map.of("status","IN_TRANSIT"),200,null);
        api("PUT","/api/v1/admin/shipments/"+shipment+"/status",adminToken,Map.of("status","DELIVERED"),200,null);
        assertThat(orders.detail(u.actor,order).order().status).isEqualTo("SHIPPED");
        api("PUT","/api/v1/admin/shipments/"+secondShipment+"/status",adminToken,Map.of("status","DELIVERED"),200,null);
        api("POST","/api/v1/user/orders/"+order+"/confirm-receipt",u.token,null,200,null);
        api("POST","/api/v1/user/orders/"+order+"/confirm-receipt",u.token,null,200,null);
        assertThat(inventory.get(sku).reservedStock).isZero();assertThat(inventory.get(sku).availableStock).isEqualTo(8);
        assertThat(orders.history(admin,order).stream().map(l->l.toStatus)).containsExactly("PENDING_PAYMENT","PAID","PROCESSING","SHIPPED","DELIVERED","COMPLETED");
        socket.sendClose(WebSocket.NORMAL_CLOSURE,"done").join();adminSocket.sendClose(WebSocket.NORMAL_CLOSURE,"done").join();
    }
    @Test void refundRejectionWithdrawalAndApprovalPreserveStateAndInventory() throws Exception {
        Shopper u=shopper("alice");long sku=sku("50",5);long order=checkout(u,sku,2,"refund-key-001");pay(u,order);orders.process(admin,order);
        long refund=orders.requestRefund(u.actor,order,"test").id;
        orders.resolveRefund(admin,refund,"REJECT","not accepted");assertThat(orders.detail(u.actor,order).order().status).isEqualTo("PROCESSING");
        refund=orders.requestRefund(u.actor,order,"test").id;orders.resolveRefund(u.actor,refund,"CANCEL","withdraw");
        assertThat(orders.detail(u.actor,order).order().status).isEqualTo("PROCESSING");
        refund=orders.requestRefund(u.actor,order,"test").id;
        api("POST","/api/v1/admin/refunds/"+refund+"/approve",adminToken,Map.of("reason","approved"),200,null);
        api("POST","/api/v1/admin/refunds/"+refund+"/complete",adminToken,null,200,null);
        assertThat(orders.detail(u.actor,order).order().status).isEqualTo("REFUNDED");assertThat(inventory.get(sku).availableStock).isEqualTo(5);assertThat(inventory.get(sku).reservedStock).isZero();
        assertThat(orders.payments(u.actor,order).getLast().status).isEqualTo("REFUNDED");
        assertThatThrownBy(()->orders.process(admin,order)).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='REFUND_APPROVE'",Long.class)).isEqualTo(1);
    }
    @Test void insufficientStockRollsBackOrderLogsCouponAndEarlierReservations(){
        Shopper u=shopper("alice");long first=sku("50",5),second=sku("10",0);carts.add(u.actor.id(),first,2);carts.add(u.actor.id(),second,1);
        Coupon c=coupon("FIXED","10",5);long owned=coupons.claim(u.actor.id(),c.id).id;
        assertThatThrownBy(()->orders.checkout(u.actor,"rollback-key-001",new OrderDtos.Checkout(u.address,owned,null))).isInstanceOf(ApiException.class);
        assertThat(inventory.get(first).availableStock).isEqualTo(5);assertThat(inventory.get(first).reservedStock).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_logs WHERE change_type='ORDER_RESERVED'",Long.class)).isZero();
        assertThat(userCoupons.selectById(owned).status).isEqualTo("AVAILABLE");assertThat(carts.view(u.actor.id()).items()).hasSize(2);
    }
    @Test void concurrentBuyersCannotOversell() throws Exception {
        Shopper a=shopper("alice"),b=shopper("bob");long sku=sku("10",1);carts.add(a.actor.id(),sku,1);carts.add(b.actor.id(),sku,1);
        List<Object> result=race(()->attempt(a,"race-alice-001"),()->attempt(b,"race-bob-001"));
        assertThat(result.stream().filter(OrderService.Detail.class::isInstance).count()).isEqualTo(1);assertThat(result.stream().filter(ApiException.class::isInstance).count()).isEqualTo(1);
        assertThat(inventory.get(sku).availableStock).isZero();assertThat(inventory.get(sku).reservedStock).isEqualTo(1);
    }
    @Test void concurrentCheckoutCancellationAndPayloadBindingAreIdempotent() throws Exception {
        Shopper u=shopper("alice");long sku=sku("10",5);carts.add(u.actor.id(),sku,2);
        List<Object> results=race(()->orders.checkout(u.actor,"same-key-001",new OrderDtos.Checkout(u.address,null,null)),()->orders.checkout(u.actor,"same-key-001",new OrderDtos.Checkout(u.address,null,null)));
        long id=((OrderService.Detail)results.getFirst()).order().id;assertThat(((OrderService.Detail)results.getLast()).order().id).isEqualTo(id);
        assertThatThrownBy(()->orders.checkout(u.actor,"same-key-001",new OrderDtos.Checkout(u.address,null,"different"))).isInstanceOf(ApiException.class);
        race(()->orders.cancel(u.actor,id,"cancel"),()->orders.cancel(u.actor,id,"cancel"));
        assertThat(inventory.get(sku).availableStock).isEqualTo(5);assertThat(inventory.get(sku).reservedStock).isZero();
    }
    @Test void defaultsOwnershipAndAddressSnapshotsSurviveDeletion() throws Exception {
        Shopper a=shopper("alice"),b=shopper("bob");long second=addresses.save(a.actor.id(),null,address(false)).id;
        race(()->{addresses.setDefault(a.actor.id(),a.address);return true;},()->{addresses.setDefault(a.actor.id(),second);return true;});
        assertThat(addresses.list(a.actor.id()).stream().filter(x->x.isDefault).count()).isEqualTo(1);
        api("DELETE","/api/v1/user/addresses/"+a.address,b.token,null,404,null);
        long order=checkout(a,sku("10",5),1,"address-key-001");addresses.delete(a.actor.id(),a.address);
        assertThat(orders.detail(a.actor,order).order().addressId).isNull();assertThat(orders.detail(a.actor,order).order().addressSnapshot.get("receiverName")).isEqualTo("Alice");
        api("GET","/api/v1/user/orders/"+order,b.token,null,404,null);
    }
    @Test void couponClaimCapacityPercentageRoundingAndRelease() throws Exception {
        Shopper a=shopper("alice"),b=shopper("bob");Coupon limited=coupon("FIXED","10",1);
        var results=race(()->claim(a,limited.id),()->claim(b,limited.id));assertThat(results.stream().filter(UserCoupon.class::isInstance).count()).isEqualTo(1);
        UserCoupon winner=(UserCoupon)results.stream().filter(UserCoupon.class::isInstance).findFirst().orElseThrow();
        assertThat(coupons.claim(winner.userId,limited.id).id).isEqualTo(winner.id);
        Coupon percentage=coupon("PERCENTAGE","10",10);long uc=coupons.claim(a.actor.id(),percentage.id).id;long sku=sku("49.90",10);carts.add(a.actor.id(),sku,2);
        var o=orders.checkout(a.actor,"coupon-key-001",new OrderDtos.Checkout(a.address,uc,null)).order();
        assertThat(o.discountAmount).isEqualByComparingTo("9.98");assertThat(o.payableAmount).isEqualByComparingTo("89.82");
        orders.cancel(a.actor,o.id,"cancel");assertThat(userCoupons.selectById(uc).status).isEqualTo("AVAILABLE");
    }
    @Test void logoutPasswordDisableAndRoleChecksRevokeExistingTokens() throws Exception {
        Shopper u=shopper("alice");api("GET","/api/v1/admin/users",u.token,null,403,null);
        api("POST","/api/v1/user/auth/logout",u.token,null,204,null);api("GET","/api/v1/user/profile",u.token,null,401,null);
        String token=auth.login(new AuthDtos.Login("alice","Password-123!"),false).accessToken();
        api("PUT","/api/v1/user/profile/password",token,new AuthDtos.Password("Password-123!","New-Password-123!"),204,null);
        api("GET","/api/v1/user/profile",token,null,401,null);String fresh=auth.login(new AuthDtos.Login("alice","New-Password-123!"),false).accessToken();
        auth.enable(admin,u.actor.id(),false,false);api("GET","/api/v1/user/profile",fresh,null,401,null);
        assertThatThrownBy(()->auth.enable(admin,admin.id(),false,true)).isInstanceOf(ApiException.class);
        String audits=api("GET","/api/v1/admin/audit-logs",adminToken,null,200,null).toString();assertThat(audits).doesNotContain("passwordHash","password_hash","Password-123!");
    }
    @Test void snapshotsCacheInvalidationOptimisticStockAndSelectedCartItems() throws Exception {
        Shopper u=shopper("alice");long sku=sku("50",10),unselected=sku("20",10);var a=carts.add(u.actor.id(),sku,1);var b=carts.add(u.actor.id(),unselected,1);carts.select(u.actor.id(),b.id,false);
        var original=jdbc.queryForObject("SELECT product_id FROM product_skus WHERE id=?",Long.class,sku);catalog.detail(original,false);
        assertThat(redis.hasKey("product:"+original)).isTrue();
        var order=orders.checkout(u.actor,"selected-key-001",new OrderDtos.Checkout(u.address,null,null));assertThat(order.items()).hasSize(1);assertThat(carts.view(u.actor.id()).items()).hasSize(1);
        catalog.sku(admin,original,sku,new CatalogDtos.SkuInput("UPDATED-SKU",Map.of("color","white"),new BigDecimal("100"),"ACTIVE",0));
        assertThat(redis.hasKey("product:"+original)).isFalse();assertThat(orders.detail(u.actor,order.order().id).items().getFirst().unitPrice).isEqualByComparingTo("50");
        var stock=inventory.get(sku);inventory.adjust(admin,sku,new CatalogDtos.Adjustment(1,stock.version,"restock"));
        assertThatThrownBy(()->inventory.adjust(admin,sku,new CatalogDtos.Adjustment(1,stock.version,"stale"))).isInstanceOf(ApiException.class);
        api("GET","/api/v1/user/products?sort=priceAsc&minPrice=10&maxPrice=200",null,null,200,null);
    }
    @Test void scheduledExpiryAutoCompletionAndCouponExpiry(){
        Shopper u=shopper("alice");long sku=sku("10",10);long pending=checkout(u,sku,2,"expired-key-001");
        jdbc.update("UPDATE orders SET created_at=? WHERE id=?",Db.now().minusMinutes(31),pending);
        long delivered=checkout(u,sku,1,"delivery-key-001");pay(u,delivered);orders.process(admin,delivered);var ship=orders.ship(admin,delivered,new OrderDtos.Ship("MOCK","AUTO-TRACK"));orders.shipmentStatus(admin,ship.id,"DELIVERED");
        jdbc.update("UPDATE orders SET delivered_at=? WHERE id=?",Db.now().minusDays(8),delivered);
        var c=coupon("FIXED","1",5);long uc=coupons.claim(u.actor.id(),c.id).id;jdbc.update("UPDATE user_coupons SET expire_at=? WHERE id=?",Db.now().minusDays(1),uc);
        new OrderTasks(orderRows,orders,coupons).run();
        assertThat(orders.detail(u.actor,pending).order().status).isEqualTo("CANCELLED");assertThat(orders.detail(u.actor,delivered).order().status).isEqualTo("COMPLETED");
        assertThat(inventory.get(sku).availableStock).isEqualTo(9);assertThat(inventory.get(sku).reservedStock).isZero();assertThat(userCoupons.selectById(uc).status).isEqualTo("EXPIRED");
        assertThat(orders.history(admin,pending).getLast().operatorType).isEqualTo("SYSTEM");
    }
    @Test void editingExistingRecordsCanAssignAndClearNullableFields() throws Exception {
        Shopper u=shopper("alice");
        long root=catalog.category(admin,null,new CatalogDtos.CategoryInput("Root",null,0)).id;
        long child=catalog.category(admin,null,new CatalogDtos.CategoryInput("Child",root,0)).id;
        assertThatThrownBy(()->catalog.category(admin,root,new CatalogDtos.CategoryInput("Cycle",child,0))).isInstanceOf(ApiException.class);
        catalog.category(admin,child,new CatalogDtos.CategoryInput("Renamed",root,1));
        assertThat(catalog.category(admin,child,new CatalogDtos.CategoryInput("Detached",null,1)).parentId).isNull();
        long product=catalog.product(admin,null,new CatalogDtos.ProductInput(root,"Product","Description","Brand","https://example.com/image.png")).id;
        api("PUT","/api/v1/admin/products/"+product,adminToken,new CatalogDtos.ProductInput(root,"New name","Description","New brand","https://example.com/new.png"),200,null);
        var cleared=catalog.product(admin,product,new CatalogDtos.ProductInput(root,"Product","Description",null,null));
        assertThat(cleared.brand).isNull();assertThat(cleared.imageUrl).isNull();
        api("PUT","/api/v1/user/addresses/"+u.address,u.token,address(true),200,null);
        var address=new AddressInput("Alice","13800138000","Shanghai","Shanghai","Pudong","New road",null,false);
        addresses.save(u.actor.id(),u.address,address);
        assertThat(addresses.owned(u.actor.id(),u.address).isDefault).isFalse();assertThat(addresses.owned(u.actor.id(),u.address).postalCode).isNull();
        auth.profile(u.actor.id(),new AuthDtos.Profile("alice","123"));auth.profile(u.actor.id(),new AuthDtos.Profile("alice",null));assertThat(auth.me(u.actor.id()).phone).isNull();
    }
    @Test void reportsExcelImagesAndOpenApiContracts() throws Exception {
        Shopper u=shopper("alice");long order=checkout(u,sku("20",10),2,"report-key-001");pay(u,order);
        assertThat(reports.overview().todayRevenue()).isEqualByComparingTo("40");assertThat(reports.sales(null,null)).hasSize(1);assertThat(reports.products(null,null,10)).hasSize(1);
        byte[] excel=mvc.perform(get("/api/v1/admin/reports/export").header("Authorization","Bearer "+adminToken)).andReturn().getResponse().getContentAsByteArray();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(excel))){assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);}
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        var upload=mvc.perform(multipart("/api/v1/admin/files/images").file(new MockMultipartFile("file","image.png","image/png",output.toByteArray())).header("Authorization","Bearer "+adminToken)).andReturn().getResponse();
        assertThat(upload.getStatus()).isEqualTo(201);String id=json.readTree(upload.getContentAsString()).get("id").asText();
        assertThat(mvc.perform(get("/api/v1/images/"+id)).andReturn().getResponse().getStatus()).isEqualTo(200);api("DELETE","/api/v1/admin/files/"+id,adminToken,null,204,null);
        var schema=api("GET","/v3/api-docs",null,null,200,null);assertThat(schema.at("/paths/~1api~1v1~1user~1orders").isMissingNode()).isFalse();
        api("POST","/api/v1/user/orders",u.token,new OrderDtos.Checkout(u.address,null,null),400,null);
    }
    @Test void notificationsAreScopedAndRolledBackEventsAreNotSent() throws Exception {
        Shopper a=shopper("alice"),b=shopper("bob");SocketListener listener=new SocketListener();
        WebSocket socket=HttpClient.newHttpClient().newWebSocketBuilder().subprotocols("orderhub","bearer."+a.token).buildAsync(URI.create("ws://localhost:"+port+"/ws/orders"),listener).join();
        TransactionTemplate tx=new TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{events.publishEvent(new OrderEvent(a.actor.id(),1,"ROLLBACK","PAID","ORDER_PAID",Db.now()));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(listener.messages.poll(300,TimeUnit.MILLISECONDS)).isNull();
        tx.executeWithoutResult(status->events.publishEvent(new OrderEvent(b.actor.id(),2,"OTHER","PAID","ORDER_PAID",Db.now())));
        assertThat(listener.messages.poll(300,TimeUnit.MILLISECONDS)).isNull();
        auth.logout(a.actor.id());tx.executeWithoutResult(status->events.publishEvent(new OrderEvent(a.actor.id(),3,"REVOKED","PAID","ORDER_PAID",Db.now())));
        assertThat(listener.messages.poll(300,TimeUnit.MILLISECONDS)).isNull();socket.abort();
    }
    @Test void legacyMigrationCopiesDataAndBalancesWithoutDeletingOriginals() throws Exception {
        String schema="migration_test_"+UUID.randomUUID().toString().replace("-","");
        Flyway first=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load();first.migrate();
        try(Connection c=dataSource.getConnection()){
            c.setSchema(schema);try(Statement s=c.createStatement()){
                s.executeUpdate("INSERT INTO app_user(id,username,password_hash,role) VALUES(10,'old_user','hash','USER')");
                s.executeUpdate("INSERT INTO product(id,sku,name,category,price) VALUES(20,'OLD-SKU','Old product','Old category',10)");
                s.executeUpdate("INSERT INTO inventory(product_id,quantity) VALUES(20,8)");
                s.executeUpdate("INSERT INTO shop_order(id,order_no,user_id,request_key,status,total_amount) VALUES(30,'OLD-ORDER',10,'old-key-001','CREATED',20)");
                s.executeUpdate("INSERT INTO order_item(order_id,product_id,product_name,unit_price,quantity,subtotal) VALUES(30,20,'Original name',10,2,20)");
            }
        }
        try{
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT reserved_stock FROM "+schema+".inventory WHERE sku_id=20",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM "+schema+".orders WHERE id=30",String.class)).isEqualTo("PENDING_PAYMENT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".app_user",Long.class)).isEqualTo(1);
        }finally{jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    private Shopper shopper(String name){var u=auth.register(new AuthDtos.Register(name+"@test.local",name,"Password-123!",null),"USER",Actor.system());long address=addresses.save(u.id,null,address(true)).id;return new Shopper(new Actor(u.id,"USER"),auth.login(new AuthDtos.Login(name,"Password-123!"),false).accessToken(),address);}
    private AddressInput address(boolean def){return new AddressInput("Alice","13800138000","Shanghai","Shanghai","Pudong","Test road 1","200000",def);}
    private long sku(String price,int stock){
        long category=catalog.category(admin,null,new CatalogDtos.CategoryInput("Category",null,0)).id;long product=catalog.product(admin,null,new CatalogDtos.ProductInput(category,"Product","Description","Brand",null)).id;
        long sku=catalog.sku(admin,product,null,new CatalogDtos.SkuInput(UUID.randomUUID().toString(),Map.of("color","black"),new BigDecimal(price),"ACTIVE",stock)).id;catalog.productStatus(admin,product,true);return sku;
    }
    private Coupon coupon(String type,String value,int quantity){return coupons.save(admin,null,new CouponInput(UUID.randomUUID().toString(),"Coupon",type,new BigDecimal(value),BigDecimal.ZERO,quantity,Db.now().minusDays(1),Db.now().plusDays(1)));}
    private long checkout(Shopper u,long sku,int quantity,String key){carts.add(u.actor.id(),sku,quantity);return orders.checkout(u.actor,key,new OrderDtos.Checkout(u.address,null,null)).order().id;}
    private void pay(Shopper u,long order){long p=orders.createPayment(u.actor,order,"MOCK").id;orders.paymentResult(u.actor,p,true);}
    private Object attempt(Shopper u,String key){try{return orders.checkout(u.actor,key,new OrderDtos.Checkout(u.address,null,null));}catch(ApiException e){return e;}}
    private Object claim(Shopper u,long coupon){try{return coupons.claim(u.actor.id(),coupon);}catch(ApiException e){return e;}}
    private JsonNode api(String method,String path,String token,Object body,int expected,String key) throws Exception {
        var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
        if(token!=null)request.header("Authorization","Bearer "+token);if(key!=null)request.header("Idempotency-Key",key);if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(request).andReturn().getResponse();assertThat(response.getStatus()).as(method+" "+path+" "+response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsString());
    }
    private List<Object> race(Callable<Object> a,Callable<Object> b) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)){
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);List<Future<Object>> futures=new ArrayList<>();
            for(var task:List.of(a,b))futures.add(executor.submit(()->{ready.countDown();start.await();return task.call();}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(futures.getFirst().get(20,TimeUnit.SECONDS),futures.getLast().get(20,TimeUnit.SECONDS));
        }
    }
    static class SocketListener implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();final StringBuilder buffer=new StringBuilder();
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){buffer.append(data);if(last){messages.offer(buffer.toString());buffer.setLength(0);}socket.request(1);return null;}
    }
}
