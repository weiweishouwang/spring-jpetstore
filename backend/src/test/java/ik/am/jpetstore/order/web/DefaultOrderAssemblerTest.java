package ik.am.jpetstore.order.web;

import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutLine;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutState;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.OrderLine;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;
import ik.am.jpetstore.order.domain.OrderModels.PaymentSummary;
import ik.am.jpetstore.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T313: order 显式 DTO/领域转换。
 *
 * <p>验证成交金额与商品文本一律取自订单快照、订单号是十进制字符串、时间是 ISO 8601 UTC、模拟
 * 支付只暴露卡型与尾号、状态标签固定（plan §5.8、ORD-19、Q07、Q11、Q15、NFR-04）。</p>
 */
class DefaultOrderAssemblerTest {

    /** 状态 P 的固定展示文案（契约冻结）。 */
    private static final String STATUS_LABEL = "已提交（模拟，未发生真实扣款）";

    private final DefaultOrderAssembler assembler = new DefaultOrderAssembler();

    @Test
    void theCheckoutResponseCarriesTheConfirmedContent() {
        CheckoutSnapshot snapshot = new CheckoutSnapshot("ck-1", "alice", "7", 4L,
                Instant.parse("2026-11-03T08:00:00Z"), Instant.parse("2026-11-03T08:15:00Z"),
                CheckoutState.OPEN, Money.cny("33.00"),
                List.of(new CheckoutLine("EST-1", 2, Money.cny("16.50"), "Angelfish", List.of("Large"))),
                address("Alice"), address("Bob"), new PaymentSummary("Visa", "1111"));

        OrderDtos.CheckoutResponseDto dto = assembler.toCheckoutResponse(snapshot);

        assertThat(dto.checkoutId()).isEqualTo("ck-1");
        assertThat(dto.cartVersion()).isEqualTo(4L);
        assertThat(dto.totalAmount()).isEqualTo("33.00");
        assertThat(dto.currency()).isEqualTo("CNY");
        assertThat(dto.payment().cardType()).isEqualTo("Visa");
        assertThat(dto.payment().lastFour())
                .as("只暴露卡型与尾号（NFR-04）")
                .isEqualTo("1111");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).productName()).isEqualTo("Angelfish");
        assertThat(dto.items().get(0).lineAmount()).isEqualTo("33.00");
        assertThat(dto.billingAddress().firstName()).isEqualTo("Alice");
        assertThat(dto.shippingAddress().firstName()).isEqualTo("Bob");
    }

    @Test
    void theOrderSummaryUsesTheDecimalOrderIdAndTheFixedStatusLabel() {
        OrderSummary summary = new OrderSummary("1001", Instant.parse("2026-11-03T08:00:00Z"), "P",
                Money.cny("33.00"));

        OrderDtos.OrderSummaryDto dto = assembler.toOrderSummary(summary);

        assertThat(dto.orderId())
                .as("订单号是十进制字符串，避免 JavaScript 精度丢失（plan §6.1）")
                .isEqualTo("1001");
        assertThat(dto.statusCode()).isEqualTo("P");
        assertThat(dto.statusLabel()).isEqualTo(STATUS_LABEL);
        assertThat(dto.totalAmount()).isEqualTo("33.00");
        assertThat(dto.currency()).isEqualTo("CNY");
    }

    @Test
    void theOrderCreatedResultCarriesTheSameFields() {
        OrderDtos.OrderCreatedResponseDto dto = assembler.toOrderCreated(snapshot());

        assertThat(dto.orderId()).isEqualTo("1001");
        assertThat(dto.statusLabel()).isEqualTo(STATUS_LABEL);
        assertThat(dto.totalAmount()).isEqualTo("33.00");
    }

    @Test
    void theOrderDetailKeepsTheTradedPriceAndTheProductText() {
        OrderDtos.OrderDetailDto dto = assembler.toOrderDetail(snapshot());

        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).unitPrice()).isEqualTo("16.50");
        assertThat(dto.items().get(0).lineAmount()).isEqualTo("33.00");
        assertThat(dto.items().get(0).productName()).isEqualTo("Angelfish");
        assertThat(dto.billingAddress().firstName()).isEqualTo("Alice");
        assertThat(dto.payment().lastFour()).isEqualTo("1111");
        assertThat(dto.courier()).isNull();
        assertThat(dto.region()).isNull();
    }

    private OrderSnapshot snapshot() {
        return new OrderSnapshot("1001", "alice", Instant.parse("2026-11-03T08:00:00Z"), "P", Money.cny("33.00"),
                address("Alice"), address("Bob"), new PaymentSummary("Visa", "1111"), null, null,
                List.of(new OrderLine("EST-1", "Angelfish", List.of("Large"), 2, Money.cny("16.50"))));
    }

    private Address address(String firstName) {
        return new Address(firstName, "Last", "1-2-3 Street", null, "City", "State", "12345", "JP");
    }
}
