package ik.am.jpetstore.order.web;

import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutLine;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.OrderLine;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;
import ik.am.jpetstore.order.domain.OrderModels.PaymentSummary;
import ik.am.jpetstore.shared.domain.Money;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * T314: order 显式 DTO/领域转换。
 *
 * <p>成交金额与商品文本一律取自订单或确认快照，绝不按当前商品价格重新计算（ADR-06、ORD-19、
 * D05）。订单号是十进制字符串，时间是 ISO 8601 UTC（plan §6.1、Q11）。模拟支付只暴露卡型与
 * 尾号（Q15、NFR-04）。</p>
 *
 * <p>状态 P 的展示文案是冻结契约的一部分：已提交（模拟，未发生真实扣款）（Q07）。</p>
 */
@Component
public class DefaultOrderAssembler implements OrderAssembler {

    /** 状态 P 的固定展示文案。 */
    public static final String STATUS_LABEL = "已提交（模拟，未发生真实扣款）";

    @Override
    public OrderDtos.CheckoutResponseDto toCheckoutResponse(CheckoutSnapshot snapshot) {
        List<OrderDtos.OrderLineDto> items = snapshot.lines().stream().map(this::toOrderLine).toList();
        return new OrderDtos.CheckoutResponseDto(snapshot.checkoutId(), snapshot.cartVersion(), snapshot.createdAt(),
                snapshot.expiresAt(), toDto(snapshot.billingAddress()), toDto(snapshot.shippingAddress()),
                toDto(snapshot.payment()), items, snapshot.totalAmount().toPlainString(),
                snapshot.totalAmount().currency().getCurrencyCode());
    }

    @Override
    public OrderDtos.OrderSummaryDto toOrderSummary(OrderSummary summary) {
        return new OrderDtos.OrderSummaryDto(summary.orderId(), summary.createdAt(), summary.statusCode(),
                STATUS_LABEL, summary.totalAmount().toPlainString(),
                summary.totalAmount().currency().getCurrencyCode());
    }

    @Override
    public OrderDtos.OrderCreatedResponseDto toOrderCreated(OrderSnapshot snapshot) {
        return new OrderDtos.OrderCreatedResponseDto(snapshot.orderId(), snapshot.createdAt(), snapshot.statusCode(),
                STATUS_LABEL, snapshot.totalAmount().toPlainString(),
                snapshot.totalAmount().currency().getCurrencyCode());
    }

    @Override
    public OrderDtos.OrderDetailDto toOrderDetail(OrderSnapshot snapshot) {
        List<OrderDtos.OrderLineDto> items = snapshot.lines().stream().map(this::toOrderLine).toList();
        return new OrderDtos.OrderDetailDto(snapshot.orderId(), snapshot.createdAt(), snapshot.statusCode(),
                STATUS_LABEL, snapshot.totalAmount().toPlainString(),
                snapshot.totalAmount().currency().getCurrencyCode(), items, toDto(snapshot.billingAddress()),
                toDto(snapshot.shippingAddress()), toDto(snapshot.payment()), snapshot.courier(), snapshot.region());
    }

    private OrderDtos.OrderLineDto toOrderLine(CheckoutLine line) {
        Money lineAmount = line.unitPrice().multiply(line.quantity());
        return new OrderDtos.OrderLineDto(line.itemId(), line.productName(), line.attributes(), line.quantity(),
                line.unitPrice().toPlainString(), lineAmount.toPlainString());
    }

    private OrderDtos.OrderLineDto toOrderLine(OrderLine line) {
        Money lineAmount = line.lineAmount();
        return new OrderDtos.OrderLineDto(line.itemId(), line.productName(), line.attributes(), line.quantity(),
                line.unitPrice().toPlainString(), lineAmount.toPlainString());
    }

    private OrderDtos.AddressDto toDto(Address address) {
        return new OrderDtos.AddressDto(address.firstName(), address.lastName(), address.address1(),
                address.address2(), address.city(), address.state(), address.zip(), address.country());
    }

    private OrderDtos.PaymentSummaryDto toDto(PaymentSummary payment) {
        return new OrderDtos.PaymentSummaryDto(payment.cardType(), payment.lastFour());
    }
}
