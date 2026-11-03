package ik.am.jpetstore.order.web;

import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;

/**
 * T215: order 显式 DTO/领域转换接口。
 *
 * <p>只声明签名；真实转换器在 Phase 7 落地。成交金额与商品文本一律取自订单快照，不得按当前
 * 商品价格重新计算（ADR-06、D05）。</p>
 */
public interface OrderAssembler {

    /** 确认快照 → 对外确认响应。 */
    OrderDtos.CheckoutResponseDto toCheckoutResponse(CheckoutSnapshot snapshot);

    /** 订单快照 → 订单摘要。 */
    OrderDtos.OrderSummaryDto toOrderSummary(OrderSnapshot snapshot);

    /** 订单快照 → 订单创建结果。 */
    OrderDtos.OrderCreatedResponseDto toOrderCreated(OrderSnapshot snapshot);

    /** 订单快照 → 订单详情。 */
    OrderDtos.OrderDetailDto toOrderDetail(OrderSnapshot snapshot);
}
