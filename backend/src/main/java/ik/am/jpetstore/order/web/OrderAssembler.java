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

    /**
     * 订单摘要 → 对外摘要。
     *
     * <p>T464（补遗）：Phase 6 实施时确认列表查询返回的是轻量摘要而不是完整快照（原签名要求
     * OrderSnapshot），为避免为了转换摘要而加载每张订单的全部明细，参数改为订单摘要本身。</p>
     */
    OrderDtos.OrderSummaryDto toOrderSummary(ik.am.jpetstore.order.domain.OrderModels.OrderSummary summary);

    /** 订单快照 → 订单创建结果。 */
    OrderDtos.OrderCreatedResponseDto toOrderCreated(OrderSnapshot snapshot);

    /** 订单快照 → 订单详情。 */
    OrderDtos.OrderDetailDto toOrderDetail(OrderSnapshot snapshot);
}
