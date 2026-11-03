package ik.am.jpetstore.order.web;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T299: 确认快照 REST适配。
 *
 * <p>覆盖认证与 CSRF、字段错误 400、版本或变价 409、成功 201、客户端不能覆盖服务端内容，以及
 * 控制器不开事务（ORD-01～07、AT-09/10、AT-R05/06、plan §6.2）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartCommand cartCommand;

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
        given(assembler.toCheckoutResponse(ArgumentMatchers.any())).willAnswer(call -> toDto(call.getArgument(0)));
    }

    @Test
    void creatingASnapshotReturns201() throws Exception {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        mockMvc.perform(post("/api/checkouts").with(user("alice")).with(csrf())
                        .contentType("application/json").content(requestBody(cart.version())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutId").isNotEmpty())
                .andExpect(jsonPath("$.cartVersion").value(cart.version()))
                .andExpect(jsonPath("$.totalAmount").value("16.50"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.payment.cardType").value("Visa"))
                .andExpect(jsonPath("$.payment.lastFour").value("1111"))
                .andExpect(jsonPath("$.billingAddress.firstName").value("Alice"))
                .andExpect(jsonPath("$.shippingAddress.firstName").value("Bob"))
                .andExpect(jsonPath("$.items[0].itemId").value("EST-1"))
                .andExpect(jsonPath("$.items[0].productName").value("Angelfish"));
    }

    @Test
    void authenticationIsRequired() throws Exception {
        mockMvc.perform(post("/api/checkouts").with(csrf())
                        .contentType("application/json").content(requestBody(0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void csrfIsRequired() throws Exception {
        mockMvc.perform(post("/api/checkouts").with(user("alice"))
                        .contentType("application/json").content(requestBody(0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void anInvalidFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/checkouts").with(user("alice")).with(csrf())
                        .contentType("application/json")
                        .content("{\"cartVersion\":0,\"billingAddress\":{\"firstName\":\"Alice\"},"
                                + "\"shippingAddress\":{},\"payment\":{\"cardType\":\"Visa\","
                                + "\"cardNumber\":\"FAKE-1\",\"expiryDate\":\"12/2026\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aStaleCartVersionIsRejected() throws Exception {
        cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        mockMvc.perform(post("/api/checkouts").with(user("alice")).with(csrf())
                        .contentType("application/json").content(requestBody(99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_VERSION_CONFLICT"));
    }

    @Test
    void theClientCannotOverrideTheServerSideContent() throws Exception {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);
        String body = requestBody(cart.version())
                .replace("\"cartVersion\":" + cart.version(),
                        "\"cartVersion\":" + cart.version() + ",\"totalAmount\":\"0.01\",\"userId\":\"mallory\"");

        mockMvc.perform(post("/api/checkouts").with(user("alice")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value("16.50"));
    }

    @Test
    void theControllerDoesNotOpenATransaction() {
        assertThat(CheckoutController.class.getAnnotations())
                .as("事务在应用服务里，控制器只做协议适配")
                .noneMatch(annotation -> annotation.annotationType().getName()
                        .equals("org.springframework.transaction.annotation.Transactional"));
    }

    private String requestBody(long cartVersion) {
        return "{\"cartVersion\":" + cartVersion + ","
                + "\"billingAddress\":{\"firstName\":\"Alice\",\"lastName\":\"Ishikawa\","
                + "\"address1\":\"1-2-3 Sakuragaoka\",\"address2\":null,\"city\":\"Shibuya\","
                + "\"state\":\"Tokyo\",\"zip\":\"150-0001\",\"country\":\"JP\"},"
                + "\"shippingAddress\":{\"firstName\":\"Bob\",\"lastName\":\"Yamada\","
                + "\"address1\":\"4-5-6 Omotesando\",\"address2\":\"Apt 301\",\"city\":\"Minato\","
                + "\"state\":\"Tokyo\",\"zip\":\"107-0052\",\"country\":\"JP\"},"
                + "\"payment\":{\"cardType\":\"Visa\",\"cardNumber\":\"FAKE-4111-1111-1111-1111\","
                + "\"expiryDate\":\"12/2026\"}}";
    }

    private OrderDtos.CheckoutResponseDto toDto(CheckoutSnapshot snapshot) {
        List<OrderDtos.OrderLineDto> items = snapshot.lines().stream()
                .map(line -> new OrderDtos.OrderLineDto(line.itemId(), line.productName(), line.attributes(),
                        line.quantity(), line.unitPrice().toPlainString(),
                        line.unitPrice().multiply(line.quantity()).toPlainString()))
                .toList();
        return new OrderDtos.CheckoutResponseDto(snapshot.checkoutId(), snapshot.cartVersion(), snapshot.createdAt(),
                snapshot.expiresAt(), toAddressDto(snapshot.billingAddress()), toAddressDto(snapshot.shippingAddress()),
                new OrderDtos.PaymentSummaryDto(snapshot.payment().cardType(), snapshot.payment().lastFour()),
                items, snapshot.totalAmount().toPlainString(), "CNY");
    }

    private OrderDtos.AddressDto toAddressDto(ik.am.jpetstore.order.domain.OrderModels.Address address) {
        return new OrderDtos.AddressDto(address.firstName(), address.lastName(), address.address1(),
                address.address2(), address.city(), address.state(), address.zip(), address.country());
    }
}
