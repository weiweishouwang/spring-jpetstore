package ik.am.jpetstore.order.application;

import ik.am.jpetstore.order.application.InventoryService.StockDeduction;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;

/**
 * T289: 同事务库存条件扣减。
 *
 * <p>覆盖正数量校验、影响行数必须为 1、按 SKU 升序的统一锁顺序，以及任一项不足时整单回滚
 * （INV-01～03、D02、Q02、AT-R01）。服务不引入独立提交，失败由外层事务统一回滚。</p>
 */
@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ik.am.jpetstore.order.infrastructure.InventoryMapper inventoryMapper;

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

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void resetData() {
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
        jdbcTemplate.update("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status)"
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P'), ('EST-2', 'FI-SW-01', 10.50, 6.00, 1, 'P')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10, 0),"
                + " ('EST-2', 1, 0)");
    }

    @Test
    void aSuccessfulDeductionReducesTheStock() {
        service.deduct(List.of(new StockDeduction("EST-1", 3)));

        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .isEqualTo(7);
    }

    @Test
    void aNonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> service.deduct(List.of(new StockDeduction("EST-1", 0))))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED));

        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .isEqualTo(10);
    }

    @Test
    void insufficientStockIsRejectedAndNothingIsDeducted() {
        assertThatThrownBy(() -> service.deduct(List.of(new StockDeduction("EST-1", 2),
                new StockDeduction("EST-2", 5))))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.OUT_OF_STOCK));

        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("任一项不足时整单回滚，库存不得出现部分扣减")
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-2'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theDeductionsFollowTheAscendingSkuOrder() {
        service.deduct(List.of(new StockDeduction("EST-2", 1), new StockDeduction("EST-1", 1)));

        InOrder order = inOrder(inventoryMapper);
        order.verify(inventoryMapper).decrementStock("EST-1", 1);
        order.verify(inventoryMapper).decrementStock("EST-2", 1);
    }

    @Test
    void missingStockIsRejected() {
        assertThatThrownBy(() -> service.deduct(List.of(new StockDeduction("NOPE", 1))))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.OUT_OF_STOCK));
    }
}
