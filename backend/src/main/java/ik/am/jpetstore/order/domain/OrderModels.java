package ik.am.jpetstore.order.domain;

import java.time.Instant;
import java.util.List;

import ik.am.jpetstore.shared.domain.Money;

/**
 * T186: 订单及成交明细不可变快照类型。
 *
 * <p>成交单价、金额与商品文本在创建时固化（ORD-19、Q15、D05）：商品改价或删除都不得改变
 * 历史订单的显示金额。地址与支付信息同样是本单副本（ORD-02）。</p>
 */
public final class OrderModels {

    private OrderModels() {
    }

    /** 订单地址副本（Q01：第二行选填）。 */
    public record Address(String firstName, String lastName, String address1, String address2,
            String city, String state, String zip, String country) {
    }

    /** 模拟支付摘要：仅卡型与尾号，不含完整假卡号或有效期（Q15、NFR-04）。 */
    public record PaymentSummary(String cardType, String lastFour) {
    }

    /**
     * 成交明细快照。
     *
     * @param quantity  正整数（1～9999，Q15）
     * @param unitPrice 创建订单时确认的成交单价
     */
    public record OrderLine(String itemId, String productName, List<String> attributes, int quantity,
            Money unitPrice) {

        /** 明细金额 = 成交单价 × 数量（ORD-19）。 */
        public Money lineAmount() {
            return unitPrice.multiply(quantity);
        }
    }

    /**
     * 订单快照。
     *
     * @param statusCode 初始状态 P = 已提交（模拟，未发生真实扣款）（Q07）
     * @param courier    承运商，允许为空（Q07）
     * @param region     地区，允许为空（Q07）
     */
    public record OrderSnapshot(String orderId, String userId, Instant createdAt, String statusCode,
            Money totalAmount, Address billingAddress, Address shippingAddress, PaymentSummary payment,
            String courier, String region, List<OrderLine> lines) {
    }

    /** 订单列表摘要：本人订单号、日期与总额（ORD-17）。 */
    public record OrderSummary(String orderId, Instant createdAt, String statusCode, Money totalAmount) {
    }
}
