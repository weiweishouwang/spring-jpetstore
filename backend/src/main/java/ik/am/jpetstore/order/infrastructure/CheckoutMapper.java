package ik.am.jpetstore.order.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * T269: Checkout Mapper签名与类型约定。
 *
 * <p>确认快照保存服务端价格、购物车版本、两组地址和模拟支付摘要，有效期 15 分钟（Q03）。
 * 快照既不锁价也不预占库存；原始模拟卡号不落库，只有卡型与尾号（Q15、NFR-04）。行类型是
 * 持久层扁平快照，SQL 定义在 mapper/order/CheckoutMapper.xml（T271）。</p>
 */
@Mapper
public interface CheckoutMapper {

    /** 快照状态：可提交。 */
    String STATE_OPEN = "OPEN";

    /** 快照状态：已关联一张订单，不可再次消费。 */
    String STATE_CONSUMED = "CONSUMED";

    /** 快照状态：已过期。 */
    String STATE_EXPIRED = "EXPIRED";

    /** checkout 表行。 */
    record CheckoutRow(String checkoutId, String userId, Long cartId, Long cartVersion, String state,
            Instant createdAt, Instant expiresAt, String totalAmount,
            String billFirstName, String billLastName, String billAddr1, String billAddr2, String billCity,
            String billState, String billZip, String billCountry,
            String shipFirstName, String shipLastName, String shipAddr1, String shipAddr2, String shipCity,
            String shipState, String shipZip, String shipCountry,
            String cardType, String cardLastFour) {
    }

    /**
     * checkout_item 表行：确认时的明细、成交单价与商品文本快照。
     *
     * @param productName    确认时的商品名；商品之后被改名或删除也不影响已确认内容（Q15、D05）
     * @param itemAttribute  确认时的规格描述，允许为空
     */
    record CheckoutItemRow(String checkoutId, Integer lineNumber, String itemId, Integer quantity,
            String unitPrice, String productName, String itemAttribute) {
    }

    /** 写入确认快照。 */
    int insertCheckout(CheckoutRow row);

    /** 写入一条快照明细。 */
    int insertCheckoutItem(CheckoutItemRow row);

    /** 读取快照。 */
    CheckoutRow selectCheckout(@Param("checkoutId") String checkoutId);

    /** 锁定快照；提交订单时必须先锁快照再锁购物车（plan §5.6）。 */
    CheckoutRow selectCheckoutForUpdate(@Param("checkoutId") String checkoutId);

    /** 快照明细，按行号升序。 */
    List<CheckoutItemRow> selectCheckoutItems(@Param("checkoutId") String checkoutId);

    /**
     * 修改快照状态（消费、过期）。
     *
     * @return 影响行数；状态已经相同时为 0，保证同一快照只能被消费一次
     */
    int updateCheckoutState(@Param("checkoutId") String checkoutId, @Param("state") String state);
}
