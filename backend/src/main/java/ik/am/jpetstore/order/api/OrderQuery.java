package ik.am.jpetstore.order.api;

import java.util.List;
import java.util.Optional;

import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;

/**
 * T200: 本人订单列表/详情端口（ORD-17、ORD-18、NFR-02）。
 *
 * <p>列表与详情都必须在服务端按当前用户过滤；他人订单与不存在订单统一视为不可见。
 * 列表按日期倒序、订单号稳定排序，首期不分页（Q11）。</p>
 */
public interface OrderQuery {

    /** 本人订单列表（最新优先）。 */
    List<OrderSummary> listOwnOrders(String userId);

    /** 本人订单详情；他人订单与不存在订单均返回空。 */
    Optional<OrderSnapshot> findOwnOrder(String userId, String orderId);
}
