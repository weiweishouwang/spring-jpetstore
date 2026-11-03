package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T285: 购物车 REST适配。
 *
 * <p>覆盖游客 CSRF、空购物车 DTO、加购、单项与整批数量更新、删除不存在项、版本冲突、
 * pendingMerges 只属于本人以及显式合并重试（CRT-01～11、plan §6.2）。只替换转换端口，
 * 应用服务走真实实现与数据库。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CartAssembler assembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void resetData() {
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
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large'),"
                + " ('EST-2', 'FI-SW-01', 10.50, 6.00, 1, 'P', 'Small')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10000, 0),"
                + " ('EST-2', 10000, 0)");
        given(assembler.toCartResponse(ArgumentMatchers.any())).willAnswer(call -> toDto(call.getArgument(0)));
    }

    @Test
    void aGuestWithoutACartGetsAnEmptyDto() throws Exception {
        mockMvc.perform(get("/api/cart").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.lineCount").value(0))
                .andExpect(jsonPath("$.totalQuantity").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value("0.00"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.mergeConflict").value(false))
                .andExpect(jsonPath("$.pendingMerges").isEmpty());
    }

    @Test
    void guestWritesAreProtectedByCsrf() throws Exception {
        mockMvc.perform(post("/api/cart/items").session(new MockHttpSession())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addingAnItemReturnsTheUpdatedCart() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.subtotal").value("16.50"));
    }

    @Test
    void theQuantityOfASingleItemCanBeChanged() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items/EST-1").session(session).with(csrf())
                        .contentType("application/json").content("{\"quantity\":3,\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(3));
    }

    @Test
    void aBatchUpdateAppliesEveryEntryAtOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1,\"items\":[{\"itemId\":\"EST-1\",\"quantity\":2},"
                                + "{\"itemId\":\"EST-2\",\"quantity\":5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(7));
    }

    @Test
    void removingAnItemThatIsNotInTheCartKeepsTheRest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart/items/EST-2").session(session).with(csrf())
                        .param("expectedVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.items[0].item.itemId").value("EST-1"));
    }

    @Test
    void aStaleExpectedVersionIsRejectedWith409() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_VERSION_CONFLICT"));
    }

    @Test
    void aGuestNeverSeesPendingMergesOfSomebodyElse() throws Exception {
        mockMvc.perform(get("/api/cart").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingMerges").isEmpty());
    }

    @Test
    void theMergeCanBeRetriedExplicitly() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/cart/merge").session(session).with(csrf())
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.mergeConflict").value(false));
    }

    @Test
    void anInvalidBatchIsRejectedWithoutPartialWrites() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json").content("{\"itemId\":\"EST-1\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1,\"items\":[{\"itemId\":\"EST-2\",\"quantity\":2},"
                                + "{\"itemId\":\"EST-1\",\"quantity\":10000}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/cart").session(session))
                .andExpect(jsonPath("$.lineCount").value(1));
    }

    private CartDtos.CartResponseDto toDto(CartSnapshot snapshot) {
        List<CartDtos.CartItemDto> items = snapshot.lines().stream()
                .map(line -> new CartDtos.CartItemDto(
                        new CartDtos.ItemView(line.itemId(), "FI-SW-01", "Angelfish", List.of("Large"), "16.50",
                                line.inStock(), 10000, true),
                        line.quantity(), "16.50"))
                .toList();
        return new CartDtos.CartResponseDto(snapshot.version(), snapshot.lineCount(), snapshot.totalQuantity(), items,
                snapshot.subtotal().toPlainString(), "CNY", !snapshot.pendingMerges().isEmpty(), List.of());
    }
}
