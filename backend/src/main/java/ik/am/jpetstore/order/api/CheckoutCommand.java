package ik.am.jpetstore.order.api;

import java.util.Optional;

import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels;

/**
 * T198: 创建与读取本人确认快照端口（Q03）。
 *
 * <p>创建快照时锁定当前购物车并校验版本、非空明细与有效可售项，读取服务端价格后生成快照。
 * 快照不预占库存、不锁价；到期、价格或购物车内容变化都要求重新确认。持有 checkoutId
 * 不替代身份授权：读取只允许归属人本人。</p>
 */
public interface CheckoutCommand {

    /** 创建确认快照。 */
    CheckoutSnapshot create(CreateCheckoutRequest request);

    /** 读取本人快照；不存在或不属于本人时返回空。 */
    Optional<CheckoutSnapshot> findOwnCheckout(String userId, String checkoutId);

    /**
     * 创建确认快照命令。
     *
     * @param userId      当前认证身份解析出的用户
     * @param cartVersion 创建快照时的购物车版本
     */
    record CreateCheckoutRequest(String userId, long cartVersion,
            OrderModels.Address billingAddress, OrderModels.Address shippingAddress, FakePayment payment) {
    }

    /**
     * 模拟支付输入（Q05、Q15）。
     *
     * @param cardNumber 假卡号，FAKE- 前缀；只用于模拟，禁止记录日志或进入响应
     * @param expiryDate MM/YYYY 格式；不做未来日期校验
     */
    record FakePayment(String cardType, String cardNumber, String expiryDate) {
    }
}
