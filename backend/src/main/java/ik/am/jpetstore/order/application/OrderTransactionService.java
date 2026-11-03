package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCheckoutAccess;
import ik.am.jpetstore.cart.domain.CartModels.CartLine;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.catalog.api.PricingQuery;
import ik.am.jpetstore.order.application.InventoryService.StockDeduction;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.OrderLine;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.PaymentSummary;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.order.domain.OrderRequestModels.ReplayableResponse;
import ik.am.jpetstore.order.domain.OrderRequestModels.RequestDigest;
import ik.am.jpetstore.order.infrastructure.CheckoutMapper;
import ik.am.jpetstore.order.infrastructure.OrderMapper;
import ik.am.jpetstore.order.infrastructure.OrderRequestMapper;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * T294: 下单本地事务。
 *
 * <p>事务内顺序固定（plan §5.6）：占用幂等键 → 锁确认快照 → 锁购物车 → 按 SKU 升序扣减库存 →
 * 写订单头与成交明细与初始状态 → 消费确认快照与购物车 → 保存可重放响应。所有步骤在同一个
 * 事务里，任一步失败整体回滚：失败后不留下订单、不扣库存、不消费购物车，幂等占位也一起回滚
 * （NFR-01、NFR-03、AT-11/12、AT-R03/08）。</p>
 *
 * <p>确认快照过期、购物车内容变化或商品变价时要求重新确认，不静默改变已确认内容
 * （ORD-06～07、Q03、D11）。库存不足整单回滚，绝不允许超卖（Q02、INV-02）。</p>
 */
@Service
public class OrderTransactionService {

    /** 幂等记录保留时长：与客户端重试窗口一致（plan §5.7）。 */
    public static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(1);

    /** 订单创建成功的 HTTP 状态。 */
    public static final int CREATED_STATUS = 201;

    private final OrderMapper orderMapper;

    private final OrderRequestMapper orderRequestMapper;

    private final CheckoutMapper checkoutMapper;

    private final CartCheckoutAccess cartCheckoutAccess;

    private final InventoryService inventoryService;

    private final PricingQuery pricingQuery;

    private final Clock clock;

    private final ObjectMapper objectMapper;

    public OrderTransactionService(OrderMapper orderMapper, OrderRequestMapper orderRequestMapper,
            CheckoutMapper checkoutMapper, CartCheckoutAccess cartCheckoutAccess, InventoryService inventoryService,
            PricingQuery pricingQuery, Clock clock, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderRequestMapper = orderRequestMapper;
        this.checkoutMapper = checkoutMapper;
        this.cartCheckoutAccess = cartCheckoutAccess;
        this.inventoryService = inventoryService;
        this.pricingQuery = pricingQuery;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建订单。
     *
     * @param userId  当前认证身份解析出的用户
     * @param checkoutId 服务端确认快照标识
     * @param key     客户端幂等键
     * @param digest  请求摘要；相同键不同摘要由外层判定为冲突
     * @return 订单快照与可重放响应
     */
    @Transactional
    public OrderPlacement placeOrder(String userId, String checkoutId, IdempotencyKey key, RequestDigest digest) {
        Instant now = now();

        // 1. 占用幂等键：占位与订单在同一事务，失败时一起回滚（plan §5.7）。
        orderRequestMapper.insertOrderRequest(new OrderRequestMapper.OrderRequestRow(null, userId,
                OrderRequestMapper.OPERATION_PLACE_ORDER, key.value(), digest.value(), null, null, null, null, now,
                now.plus(IDEMPOTENCY_RETENTION)));

        // 2. 锁确认快照：校验归属、状态与有效期（ORD-01、ORD-06）。
        CheckoutMapper.CheckoutRow checkout = checkoutMapper.selectCheckoutForUpdate(checkoutId);
        if (checkout == null || !userId.equals(checkout.userId())) {
            throw new BusinessFailure(BusinessFailure.Code.ORDER_NOT_FOUND, "确认快照不存在");
        }
        if (!CheckoutMapper.STATE_OPEN.equals(checkout.state())) {
            throw new BusinessFailure(BusinessFailure.Code.CHECKOUT_ALREADY_SUBMITTED,
                    "该确认快照已经提交过订单");
        }
        if (!checkout.expiresAt().isAfter(now)) {
            throw new BusinessFailure(BusinessFailure.Code.CHECKOUT_STALE, "确认快照已过期，请重新确认");
        }

        // 3. 锁购物车：版本必须仍与确认时一致，否则内容已变化（ORD-06、Q03）。
        CartSnapshot cart = lockCart(userId, checkout.cartVersion());

        // 4. 明细必须与确认内容一致：内容变化要求重新确认，不静默替换交易内容（ORD-07、D11）。
        List<CheckoutMapper.CheckoutItemRow> items = checkoutMapper.selectCheckoutItems(checkoutId);
        if (!sameContent(cart, items)) {
            throw new BusinessFailure(BusinessFailure.Code.CHECKOUT_STALE, "购物车内容已变化，请重新确认");
        }

        // 5. 提交时检价：变价要求重新确认（Q03）。
        for (CheckoutMapper.CheckoutItemRow item : items) {
            Money currentPrice = pricingQuery.priceOf(item.itemId());
            if (!currentPrice.toPlainString().equals(item.unitPrice())) {
                throw new BusinessFailure(BusinessFailure.Code.CHECKOUT_STALE, "商品价格已变化，请重新确认");
            }
        }

        // 6. 库存条件扣减：任一项不足整单回滚（Q02、INV-02）。
        inventoryService.deduct(items.stream()
                .map(item -> new StockDeduction(item.itemId(), item.quantity()))
                .toList());

        // 7. 写订单头、成交明细快照与初始状态（ORD-09、ORD-19、Q07、Q15）。
        Money total = Money.zero();
        OrderMapper.NewOrder order = new OrderMapper.NewOrder();
        order.setUserId(userId);
        order.setOrderDate(now);
        order.setBillFirstName(checkout.billFirstName());
        order.setBillLastName(checkout.billLastName());
        order.setBillAddr1(checkout.billAddr1());
        order.setBillAddr2(checkout.billAddr2());
        order.setBillCity(checkout.billCity());
        order.setBillState(checkout.billState());
        order.setBillZip(checkout.billZip());
        order.setBillCountry(checkout.billCountry());
        order.setShipFirstName(checkout.shipFirstName());
        order.setShipLastName(checkout.shipLastName());
        order.setShipAddr1(checkout.shipAddr1());
        order.setShipAddr2(checkout.shipAddr2());
        order.setShipCity(checkout.shipCity());
        order.setShipState(checkout.shipState());
        order.setShipZip(checkout.shipZip());
        order.setShipCountry(checkout.shipCountry());
        order.setTotalAmount(checkout.totalAmount());
        order.setStatus(OrderMapper.STATUS_SUBMITTED);
        order.setCourier(null);
        order.setRegion(null);
        order.setCardType(checkout.cardType());
        order.setCardLastFour(checkout.cardLastFour());
        order.setCheckoutId(checkoutId);
        orderMapper.insertOrder(order);
        Long orderId = order.getOrderId();

        int lineNumber = 1;
        for (CheckoutMapper.CheckoutItemRow item : items) {
            Money unitPrice = Money.cny(item.unitPrice());
            total = total.add(unitPrice.multiply(item.quantity()));
            orderMapper.insertOrderLine(new OrderMapper.OrderLineRow(orderId, lineNumber++, item.itemId(),
                    item.quantity(), item.unitPrice(), item.productName(), item.itemAttribute()));
        }
        orderMapper.insertOrderStatus(new OrderMapper.OrderStatusRow(orderId, 1, OrderMapper.STATUS_SUBMITTED, now));

        // 8. 消费确认快照与购物车：同一快照只能关联一张订单（Q04、ORD-12）。
        checkoutMapper.updateCheckoutState(checkoutId, CheckoutMapper.STATE_CONSUMED);
        cartCheckoutAccess.consume(CartOwner.account(userId), cart.cartId(), cart.version());

        OrderSnapshot snapshot = new OrderSnapshot(String.valueOf(orderId), userId, now,
                OrderMapper.STATUS_SUBMITTED, total, billingAddress(checkout), shippingAddress(checkout),
                new PaymentSummary(checkout.cardType(), checkout.cardLastFour()), null, null,
                linesOf(items));
        String location = "/api/orders/" + orderId;
        String body = responseBody(snapshot);
        orderRequestMapper.completeOrderRequest(userId, OrderRequestMapper.OPERATION_PLACE_ORDER, key.value(),
                orderId, CREATED_STATUS, location, body);
        return new OrderPlacement(snapshot, new ReplayableResponse(CREATED_STATUS, location, body));
    }

    private CartSnapshot lockCart(String userId, long cartVersion) {
        try {
            return cartCheckoutAccess.lockActiveCart(CartOwner.account(userId), cartVersion);
        } catch (BusinessFailure e) {
            if (e.code() == BusinessFailure.Code.CART_VERSION_CONFLICT) {
                throw new BusinessFailure(BusinessFailure.Code.CHECKOUT_STALE, "购物车内容已变化，请重新确认");
            }
            throw e;
        }
    }

    /** 购物车明细必须与确认明细逐项一致（数量相同、商品相同）。 */
    private boolean sameContent(CartSnapshot cart, List<CheckoutMapper.CheckoutItemRow> items) {
        if (cart.lines().size() != items.size()) {
            return false;
        }
        Map<String, Integer> cartQuantities = new LinkedHashMap<>();
        for (CartLine line : cart.lines()) {
            cartQuantities.put(line.itemId(), line.quantity());
        }
        for (CheckoutMapper.CheckoutItemRow item : items) {
            Integer quantity = cartQuantities.get(item.itemId());
            if (quantity == null || quantity != item.quantity()) {
                return false;
            }
        }
        return true;
    }

    private List<OrderLine> linesOf(List<CheckoutMapper.CheckoutItemRow> items) {
        return items.stream()
                .map(item -> new OrderLine(item.itemId(), item.productName(), attributesOf(item.itemAttribute()),
                        item.quantity(), Money.cny(item.unitPrice())))
                .toList();
    }

    private List<String> attributesOf(String itemAttribute) {
        if (itemAttribute == null || itemAttribute.isBlank()) {
            return List.of();
        }
        return List.of(itemAttribute.split(" / "));
    }

    private Address billingAddress(CheckoutMapper.CheckoutRow checkout) {
        return new Address(checkout.billFirstName(), checkout.billLastName(), checkout.billAddr1(),
                checkout.billAddr2(), checkout.billCity(), checkout.billState(), checkout.billZip(),
                checkout.billCountry());
    }

    private Address shippingAddress(CheckoutMapper.CheckoutRow checkout) {
        return new Address(checkout.shipFirstName(), checkout.shipLastName(), checkout.shipAddr1(),
                checkout.shipAddr2(), checkout.shipCity(), checkout.shipState(), checkout.shipZip(),
                checkout.shipCountry());
    }

    /**
     * 可重放的响应体。
     *
     * <p>只包含订单号、时间、状态、金额与币种：不含 Set-Cookie、CSRF、密码或完整模拟卡号
     * （plan §5.7、NFR-04）。</p>
     */
    private String responseBody(OrderSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", snapshot.orderId());
        payload.put("createdAt", snapshot.createdAt().toString());
        payload.put("statusCode", snapshot.statusCode());
        payload.put("totalAmount", snapshot.totalAmount().toPlainString());
        payload.put("currency", snapshot.totalAmount().currency().getCurrencyCode());
        return objectMapper.writeValueAsString(payload);
    }

    /** 存储与传输使用 UTC，精度到微秒——与 DATETIME(6) 一致，重放时读回同一时刻。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 下单结果。
     *
     * @param snapshot 订单快照
     * @param response 可重放响应：状态、Location 与响应体
     */
    public record OrderPlacement(OrderSnapshot snapshot, ReplayableResponse response) {
    }
}
