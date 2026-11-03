package ik.am.jpetstore.order.application;

import ik.am.jpetstore.order.api.OrderQuery;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.OrderLine;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;
import ik.am.jpetstore.order.domain.OrderModels.PaymentSummary;
import ik.am.jpetstore.order.infrastructure.OrderMapper;
import ik.am.jpetstore.shared.domain.Money;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * T298: 本人历史订单快照查询。
 *
 * <p>只返回当前认证用户自己的订单：过滤在 SQL 层完成，他人订单与不存在订单统一视为不可见
 * （ORD-18、NFR-02）。列表按日期倒序、订单号稳定排序，首期不分页（Q11）。</p>
 *
 * <p>金额与明细一律取自订单自己的快照，绝不按当前商品价格重新计算：商品改价或删除都不改变
 * 历史订单的显示金额（ORD-19、Q15、D05）。</p>
 */
@Service
public class OrderQueryService implements OrderQuery {

    private final OrderMapper orderMapper;

    public OrderQueryService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummary> listOwnOrders(String userId) {
        return orderMapper.selectOrderSummaries(userId).stream()
                .map(row -> new OrderSummary(String.valueOf(row.orderId()), row.orderDate(), row.status(),
                        Money.cny(row.totalAmount())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> findOwnOrder(String userId, String orderId) {
        Long numericId;
        try {
            numericId = Long.valueOf(orderId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        OrderMapper.OrderRow row = orderMapper.selectOrder(userId, numericId);
        if (row == null) {
            return Optional.empty();
        }
        List<OrderLine> lines = orderMapper.selectOrderLines(numericId).stream()
                .map(line -> new OrderLine(line.itemId(), line.productName(), attributesOf(line.itemAttribute()),
                        line.quantity(), Money.cny(line.unitPrice())))
                .toList();
        return Optional.of(new OrderSnapshot(String.valueOf(row.orderId()), row.userId(), row.orderDate(),
                row.status(), Money.cny(row.totalAmount()),
                new Address(row.billFirstName(), row.billLastName(), row.billAddr1(), row.billAddr2(),
                        row.billCity(), row.billState(), row.billZip(), row.billCountry()),
                new Address(row.shipFirstName(), row.shipLastName(), row.shipAddr1(), row.shipAddr2(),
                        row.shipCity(), row.shipState(), row.shipZip(), row.shipCountry()),
                new PaymentSummary(row.cardType(), row.cardLastFour()), row.courier(), row.region(), lines));
    }

    private List<String> attributesOf(String itemAttribute) {
        if (itemAttribute == null || itemAttribute.isBlank()) {
            return List.of();
        }
        return List.of(itemAttribute.split(" / "));
    }
}
