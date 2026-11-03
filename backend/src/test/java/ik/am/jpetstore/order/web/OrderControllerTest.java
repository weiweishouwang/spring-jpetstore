package ik.am.jpetstore.order.web;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T301: 订单提交与查询 REST适配。
 *
 * <p>覆盖 201 + Location、Idempotency-Key 必填、重放响应一致、本人列表与详情过滤、他人订单与
 * 不存在订单统一 404，以及金额以字符串返回（ORD-01～20、AT-11/13、AT-R02/04/08、plan §6.2）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final Address BILLING = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya",
            "Tokyo", "150-0001", "JP");

    private static final Address SHIPPING = new Address("Bob", "Yamada", "4-5-6 Omotesando", "Apt 301", "Minato",
            "Tokyo", "107-0052", "JP");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartCommand cartCommand;

    @Autowired
    private ik.am.jpetstore.order.application.CheckoutApplicationService checkoutService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OrderAssembler assembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM order_request");
        jdbcTemplate.update("DELETE FROM orderstatus");
        jdbcTemplate.update("DELETE FROM lineitem");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM checkout_item");
        jdbcTemplate.update("DELETE FROM checkout");
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM item");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM supplier");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("INSERT INTO category (catid, name, description) VALUES ('FISH', 'Fish', 'd')");
        jdbcTemplate.update("INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country,"
                + " phone) VALUES (1, 'S', 'P', 'a', NULL, 'c', 's', 'z', 'C', 'p')");
        jdbcTemplate.update("INSERT INTO product (productid, category, name, description)"
                + " VALUES ('FI-SW-01', 'FISH', 'Angelfish', 'd')");
        jdbcTemplate.update("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status, attr1)"
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 100, 0)");
        given(assembler.toOrderCreated(ArgumentMatchers.any())).willAnswer(call -> toCreated(call.getArgument(0)));
        given(assembler.toOrderSummary(ArgumentMatchers.any())).willAnswer(call -> {
            ik.am.jpetstore.order.domain.OrderModels.OrderSummary summary = call.getArgument(0);
            return new OrderDtos.OrderSummaryDto(summary.orderId(), summary.createdAt(), summary.statusCode(),
                    "已提交（模拟，未发生真实扣款）", summary.totalAmount().toPlainString(), "CNY");
        });
        given(assembler.toOrderDetail(ArgumentMatchers.any())).willAnswer(call -> toDetail(call.getArgument(0)));
    }

    @Test
    void placingAnOrderReturns201WithLocation() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice");

        mockMvc.perform(post("/api/orders").with(user("alice")).with(csrf())
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{\"checkoutId\":\"" + checkout.checkoutId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.statusCode").value("P"))
                .andExpect(jsonPath("$.totalAmount").value("16.50"))
                .andExpect(jsonPath("$.currency").value("CNY"));
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice");

        mockMvc.perform(post("/api/orders").with(user("alice")).with(csrf())
                        .contentType("application/json")
                        .content("{\"checkoutId\":\"" + checkout.checkoutId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReplayReturnsTheSameResult() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice");
        String body = "{\"checkoutId\":\"" + checkout.checkoutId() + "\"}";

        String first = mockMvc.perform(post("/api/orders").with(user("alice")).with(csrf())
                        .header("Idempotency-Key", "key-1").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/orders").with(user("alice")).with(csrf())
                        .header("Idempotency-Key", "key-1").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.orderId").value(
                        java.util.regex.Pattern.compile("\"orderId\":\"(\\d+)\"")
                                .matcher(first).results().findFirst().orElseThrow().group(1)));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void theOrderListOnlyContainsOwnOrders() throws Exception {
        checkoutWith("alice");

        mockMvc.perform(get("/api/orders").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/orders").with(user("mallory")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anotherUsersOrderIsNotFound() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice");
        String body = mockMvc.perform(post("/api/orders").with(user("alice")).with(csrf())
                        .header("Idempotency-Key", "key-1").contentType("application/json")
                        .content("{\"checkoutId\":\"" + checkout.checkoutId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = java.util.regex.Pattern.compile("\"orderId\":\"(\\d+)\"")
                .matcher(body).results().findFirst().orElseThrow().group(1);

        mockMvc.perform(get("/api/orders/" + orderId).with(user("mallory")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(get("/api/orders/" + orderId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value("16.50"))
                .andExpect(jsonPath("$.items[0].productName").value("Angelfish"));
    }

    @Test
    void anUnknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/999").with(user("alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void authenticationIsRequired() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private CheckoutSnapshot checkoutWith(String userId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account(userId), "EST-1", 0);
        return checkoutService.create(new CreateCheckoutRequest(userId, cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }

    private OrderDtos.OrderCreatedResponseDto toCreated(OrderSnapshot snapshot) {
        return new OrderDtos.OrderCreatedResponseDto(snapshot.orderId(), snapshot.createdAt(), snapshot.statusCode(),
                "已提交（模拟，未发生真实扣款）", snapshot.totalAmount().toPlainString(), "CNY");
    }

    private OrderDtos.OrderDetailDto toDetail(OrderSnapshot snapshot) {
        List<OrderDtos.OrderLineDto> items = snapshot.lines().stream()
                .map(line -> new OrderDtos.OrderLineDto(line.itemId(), line.productName(), line.attributes(),
                        line.quantity(), line.unitPrice().toPlainString(),
                        line.unitPrice().multiply(line.quantity()).toPlainString()))
                .toList();
        return new OrderDtos.OrderDetailDto(snapshot.orderId(), snapshot.createdAt(), snapshot.statusCode(),
                "已提交（模拟，未发生真实扣款）", snapshot.totalAmount().toPlainString(), "CNY", items,
                toAddressDto(snapshot.billingAddress()), toAddressDto(snapshot.shippingAddress()),
                new OrderDtos.PaymentSummaryDto(snapshot.payment().cardType(), snapshot.payment().lastFour()),
                snapshot.courier(), snapshot.region());
    }

    private OrderDtos.AddressDto toAddressDto(Address address) {
        return new OrderDtos.AddressDto(address.firstName(), address.lastName(), address.address1(),
                address.address2(), address.city(), address.state(), address.zip(), address.country());
    }
}
