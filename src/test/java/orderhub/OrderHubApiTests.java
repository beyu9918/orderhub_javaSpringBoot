package orderhub;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import orderhub.api.ApiException;
import orderhub.api.Requests.*;
import orderhub.api.Views.*;
import orderhub.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderHubApiTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired ProductService products;
    @Autowired CartService cart;
    @Autowired OrderService orders;
    @Autowired JwtEncoder encoder;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM order_item");
        jdbc.update("DELETE FROM shop_order");
        jdbc.update("DELETE FROM cart_item");
        jdbc.update("DELETE FROM legacy_inventory");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM app_user WHERE role = 'USER'");
    }

    @Test
    void authValidationAndPermissions() throws Exception {
        mvc.perform(get("/api/cart/items")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        String body = "{\"username\":\"alice\",\"password\":\"Password-123!\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("Password-123!", "Wrong-123!"))).andExpect(status().isUnauthorized());
        String token = tokenFromLogin(body);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        var expired = JwtClaimsSet.builder().issuer("orderhub").subject("1").issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(300)).claim("role", "ADMIN").build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expired)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='alice'", String.class))
                .startsWith("$2").doesNotContain("Password");
    }

    @Test
    void httpCheckoutHistorySnapshotAndCancel() throws Exception {
        long alice = user("alice");
        long bob = user("bob");
        String aliceToken = auth.login(credentials("alice")).accessToken();
        String bobToken = auth.login(credentials("bob")).accessToken();
        String adminToken = auth.login(new Credentials("testadmin", "Admin-Test-123!")).accessToken();
        String productJson = json.writeValueAsString(input("sku-1", "Keyboard", "49.90", 10));
        String result = mvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(productJson)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = json.readTree(result).get("id").asLong();
        mvc.perform(get("/api/products").param("category", "electronics").param("keyword", "Key").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items/" + productId).header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cart/items").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        String orderJson = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"checkout-0001\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(99.8))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(orderJson).get("id").asLong();
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"checkout-0001\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(orderId));
        assertThat(products.stock(productId).quantity()).isEqualTo(8);
        assertThat(cart.list(alice)).isEmpty();
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        products.update(productId, input("sku-1", "New name", "100.00", 8));
        assertThat(orders.detail(alice, orderId).items().getFirst().productName()).isEqualTo("Keyboard");
        assertThat(orders.detail(alice, orderId).items().getFirst().unitPrice()).isEqualByComparingTo("49.90");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + aliceToken).param("status", "CREATED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        orders.cancel(alice, orderId);
        assertThat(products.stock(productId).quantity()).isEqualTo(10);
        assertThat(orders.history(bob, 1, 20, null).total()).isZero();
        mvc.perform(delete("/api/admin/products/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void insufficientStockRollsBackAllDeductionsAndPreservesCart() {
        long id = user("alice");
        long first = products.create(input("first", "First", "10", 5)).id();
        long second = products.create(input("second", "Second", "20", 0)).id();
        cart.put(id, first, 2);
        cart.put(id, second, 1);
        assertThatThrownBy(() -> orders.checkout(id, "rollback-001")).isInstanceOf(ApiException.class);
        assertThat(products.stock(first).quantity()).isEqualTo(5);
        assertThat(cart.list(id)).hasSize(2);
        assertThat(orders.history(id, 1, 20, null).total()).isZero();
        products.setStock(second, 1);
        assertThat(orders.checkout(id, "rollback-001").items()).hasSize(2);
    }

    @Test
    void concurrentBuyersCannotOversell() throws Exception {
        long alice = user("alice"), bob = user("bob");
        long product = products.create(input("last-one", "Last item", "10", 1)).id();
        cart.put(alice, product, 1); cart.put(bob, product, 1);
        List<Object> results = race(() -> checkoutResult(alice, "race-alice"), () -> checkoutResult(bob, "race-bob"));
        assertThat(results.stream().filter(OrderView.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(ApiException.class::isInstance).count()).isEqualTo(1);
        assertThat(products.stock(product).quantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentRetriesAndCancellationAreIdempotent() throws Exception {
        long alice = user("alice");
        long product = products.create(input("same-request", "Item", "10", 5)).id();
        cart.put(alice, product, 2);
        List<Object> results = race(() -> orders.checkout(alice, "same-key-001"), () -> orders.checkout(alice, "same-key-001"));
        assertThat(results).allMatch(OrderView.class::isInstance);
        long id = ((OrderView) results.getFirst()).id();
        assertThat(((OrderView) results.getLast()).id()).isEqualTo(id);
        assertThat(products.stock(product).quantity()).isEqualTo(3);
        race(() -> orders.cancel(alice, id), () -> orders.cancel(alice, id));
        assertThat(products.stock(product).quantity()).isEqualTo(5);
    }

    @Test
    void schemaConstraintsAndOpenApiAreAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/orders']").exists());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        long product = products.create(input("constraint", "Item", "10", 5)).id();
        assertThatThrownBy(() -> jdbc.update("UPDATE legacy_inventory SET quantity=-1 WHERE product_id=?", product))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cart_item(user_id,product_id,quantity) VALUES(999999,?,1)", product))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private String tokenFromLogin(String body) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }
    private long user(String username) { return auth.register(credentials(username)).id(); }
    private Credentials credentials(String username) { return new Credentials(username, "Password-123!"); }
    private ProductInput input(String sku, String name, String price, int stock) {
        return new ProductInput(sku, name, "Description", "electronics", new BigDecimal(price), true, stock);
    }
    private Object checkoutResult(long user, String key) {
        try { return orders.checkout(user, key); } catch (ApiException ex) { return ex; }
    }
    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : List.of(first, second)) {
                futures.add(executor.submit(() -> { ready.countDown(); start.await(); return task.call(); }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.getFirst().get(20, TimeUnit.SECONDS), futures.getLast().get(20, TimeUnit.SECONDS));
        }
    }
}
