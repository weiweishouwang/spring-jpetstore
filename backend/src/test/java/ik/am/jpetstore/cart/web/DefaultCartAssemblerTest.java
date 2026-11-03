package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.domain.CartModels.CartLine;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.cart.domain.CartModels.CartState;
import ik.am.jpetstore.cart.domain.CartModels.PendingMerge;
import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * T311: cart 显式 DTO/领域转换。
 *
 * <p>验证金额字符串化、行数与商品数量总和分别统计、待合并内容按本人持有展示，以及明细金额由
 * 服务端单价乘数量得出（plan §5.4、CRT-09、Q08）。</p>
 */
class DefaultCartAssemblerTest {

    private CatalogQuery catalogQuery;

    private DefaultCartAssembler assembler;

    @BeforeEach
    void setUp() {
        catalogQuery = mock(CatalogQuery.class);
        assembler = new DefaultCartAssembler(catalogQuery);
        given(catalogQuery.item("EST-1")).willReturn(Optional.of(new Item("EST-1", "FI-SW-01", "Angelfish",
                List.of("Large"), Money.cny("16.50"), 10, true, true)));
    }

    @Test
    void theCartResponseCountsLinesAndQuantitiesSeparately() {
        CartSnapshot snapshot = new CartSnapshot("1", CartOwner.account("alice"), 3L, CartState.ACTIVE,
                List.of(new CartLine("EST-1", 2, true)), Money.cny("33.00"), List.of());

        CartDtos.CartResponseDto dto = assembler.toCartResponse(snapshot);

        assertThat(dto.version()).isEqualTo(3L);
        assertThat(dto.lineCount()).as("行数不等于商品数量总和（CRT-09）").isEqualTo(1);
        assertThat(dto.totalQuantity()).isEqualTo(2);
        assertThat(dto.subtotal()).isEqualTo("33.00");
        assertThat(dto.currency()).isEqualTo("CNY");
        assertThat(dto.mergeConflict()).isFalse();
        assertThat(dto.pendingMerges()).isEmpty();
    }

    @Test
    void eachLineCarriesTheItemViewAndTheLineAmount() {
        CartSnapshot snapshot = new CartSnapshot("1", CartOwner.account("alice"), 1L, CartState.ACTIVE,
                List.of(new CartLine("EST-1", 2, true)), Money.cny("33.00"), List.of());

        CartDtos.CartResponseDto dto = assembler.toCartResponse(snapshot);

        assertThat(dto.items()).hasSize(1);
        CartDtos.CartItemDto item = dto.items().get(0);
        assertThat(item.item().itemId()).isEqualTo("EST-1");
        assertThat(item.item().unitPrice()).isEqualTo("16.50");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.lineAmount())
                .as("明细金额 = 服务端单价 × 数量，客户端不重算")
                .isEqualTo("33.00");
    }

    @Test
    void pendingMergesAreExposedWithTheirOwnItems() {
        CartSnapshot snapshot = new CartSnapshot("1", CartOwner.accountInSession("alice", "hash"), 2L,
                CartState.ACTIVE, List.of(new CartLine("EST-1", 1, true)), Money.cny("16.50"),
                List.of(new PendingMerge("9", List.of(new CartLine("EST-1", 9999, false)))));

        CartDtos.CartResponseDto dto = assembler.toCartResponse(snapshot);

        assertThat(dto.mergeConflict()).as("有待合并内容时提示冲突（Q08）").isTrue();
        assertThat(dto.pendingMerges()).hasSize(1);
        assertThat(dto.pendingMerges().get(0).mergeId()).isEqualTo("9");
        assertThat(dto.pendingMerges().get(0).items()).hasSize(1);
        assertThat(dto.pendingMerges().get(0).items().get(0).quantity()).isEqualTo(9999);
    }

    @Test
    void theItemViewIsResolvedFromTheCatalog() {
        CartDtos.ItemView view = assembler.toItemView("EST-1");

        assertThat(view.itemId()).isEqualTo("EST-1");
        assertThat(view.productId()).isEqualTo("FI-SW-01");
        assertThat(view.productName()).isEqualTo("Angelfish");
        assertThat(view.unitPrice()).isEqualTo("16.50");
        assertThat(view.availableQuantity()).isEqualTo(10);
        assertThat(view.purchasable()).isTrue();
    }
}
