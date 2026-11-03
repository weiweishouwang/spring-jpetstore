package ik.am.jpetstore.order.web;

import ik.am.jpetstore.order.api.OrderCommand;
import ik.am.jpetstore.order.api.OrderCommand.Placement;
import ik.am.jpetstore.order.api.OrderQuery;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.shared.error.BusinessFailure;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * T302: 订单提交与查询 REST适配。
 *
 * <p>提交订单只接受 checkoutId 与 Idempotency-Key：价格、归属、状态与明细都来自服务端快照，
 * 客户端不得覆盖（ORD-01、ORD-13、NFR-02）。幂等键必填，重试必须复用同一个键；重放返回原
 * 结果并带 Idempotency-Replayed: true（plan §5.7）。查询只返回本人订单，他人订单与不存在订单
 * 统一 404（ORD-18、NFR-02）。</p>
 */
@RestController
public class OrderController {

    private static final String VERSION = "1.0";

    private final OrderCommand orderCommand;

    private final OrderQuery orderQuery;

    private final OrderAssembler assembler;

    public OrderController(OrderCommand orderCommand, OrderQuery orderQuery, OrderAssembler assembler) {
        this.orderCommand = orderCommand;
        this.orderQuery = orderQuery;
        this.assembler = assembler;
    }

    /** 提交订单：201 + Location；幂等重放返回原结果。 */
    @PostMapping(path = "/api/orders", version = VERSION)
    public ResponseEntity<OrderDtos.OrderCreatedResponseDto> placeOrder(Authentication authentication,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        String userId = requireUsername(authentication);
        Placement placement = orderCommand.placeOrder(userId, request.checkoutId(),
                new IdempotencyKey(idempotencyKey));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/orders/" + placement.snapshot().orderId());
        if (placement.replayed()) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(assembler.toOrderCreated(placement.snapshot()));
    }

    /** 本人订单列表：最新优先，首期不分页。 */
    @GetMapping(path = "/api/orders", version = VERSION)
    public List<OrderDtos.OrderSummaryDto> orders(Authentication authentication) {
        String userId = requireUsername(authentication);
        return orderQuery.listOwnOrders(userId).stream().map(assembler::toOrderSummary).toList();
    }

    /** 本人订单详情：他人订单与不存在订单统一 404。 */
    @GetMapping(path = "/api/orders/{id}", version = VERSION)
    public OrderDtos.OrderDetailDto order(Authentication authentication, @PathVariable("id") String orderId) {
        String userId = requireUsername(authentication);
        OrderSnapshot snapshot = orderQuery.findOwnOrder(userId, orderId)
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.ORDER_NOT_FOUND, "订单不存在"));
        return assembler.toOrderDetail(snapshot);
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessFailure(BusinessFailure.Code.AUTHENTICATION_REQUIRED, "需要登录");
        }
        return authentication.getName();
    }
}
