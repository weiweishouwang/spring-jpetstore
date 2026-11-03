package ik.am.jpetstore.order.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import ik.am.jpetstore.shared.domain.Money;

/**
 * T187: 确认快照、归属、版本和状态类型（Q03、Q04）。
 *
 * <p>确认快照保存服务端价格与购物车版本，有效期 15 分钟；不锁价、不预占库存。价格或购物车
 * 内容变化、快照过期都要求重新确认，提交时不得静默改变已确认内容（ORD-06、ORD-07、D11）。</p>
 *
 * <p>T455（补遗）：Phase 6 实施时确认冻结契约缺少商品文本快照，而 OpenAPI 的确认响应要求
 * items 带 productName 与 attributes（快照不得按当前商品重算，ORD-19/D05）。因此明细追加商品名
 * 与规格快照，属追加式修正。</p>
 */
public final class CheckoutModels {

    private CheckoutModels() {
    }

    /** 快照有效期（Q03）。 */
    public static final Duration VALIDITY = Duration.ofMinutes(15);

    /**
     * 快照确认的明细。
     *
     * @param productName 商品名快照；商品之后被删除或改名也不影响确认内容（ORD-19、D05）
     * @param attributes  规格快照；与成交明细的展示字段一致（Q15）
     */
    public record CheckoutLine(String itemId, int quantity, Money unitPrice, String productName,
            List<String> attributes) {
    }

    /** 快照状态。 */
    public enum CheckoutState {
        /** 可提交。 */
        OPEN,
        /** 已关联一张订单，不可再次消费。 */
        CONSUMED,
        /** 已过期。 */
        EXPIRED
    }

    /**
     * 确认快照。
     *
     * @param userId      归属人；持有 checkoutId 不替代身份授权（ORD-01）
     * @param cartVersion 创建快照时的购物车版本
     * @param expiresAt   服务端生成的到期时间（UTC）
     */
    public record CheckoutSnapshot(String checkoutId, String userId, String cartId, long cartVersion,
            Instant createdAt, Instant expiresAt, CheckoutState state, Money totalAmount,
            List<CheckoutLine> lines, OrderModels.Address billingAddress, OrderModels.Address shippingAddress,
            OrderModels.PaymentSummary payment) {

        /** 快照是否已过期（Q03）。 */
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
