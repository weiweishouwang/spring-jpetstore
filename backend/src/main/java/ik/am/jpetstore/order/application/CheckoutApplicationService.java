package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCheckoutAccess;
import ik.am.jpetstore.cart.domain.CartModels.CartLine;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.api.PricingQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.order.api.CheckoutCommand;
import ik.am.jpetstore.order.domain.CheckoutModels;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutLine;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutState;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderModels.PaymentSummary;
import ik.am.jpetstore.order.infrastructure.CheckoutMapper;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * T288: 服务端确认快照应用服务。
 *
 * <p>创建快照时锁定当前购物车并校验版本、非空明细与有效可售项，然后读取服务端价格生成快照
 * （plan §5.5、Q03）。快照保存服务端价格与购物车版本，有效期 15 分钟；不锁价、不预占库存，
 * 因此提交订单时仍要检价（ORD-06～07、D11）。</p>
 *
 * <p>客户端不能伪造快照：请求只带购物车版本、两组地址和模拟支付，价格、归属和明细全部由服务端
 * 得出（ORD-01、ORD-13、NFR-02）。原始模拟卡号不落库、不进日志，只保留卡型与尾号（Q15、NFR-04）。</p>
 */
@Service
public class CheckoutApplicationService implements CheckoutCommand {

    /** 模拟卡号前缀：只接受演示用的假卡号（Q05、Q15）。 */
    private static final String FAKE_CARD_PREFIX = "FAKE-";

    /** 有效期格式 MM/YYYY。 */
    private static final Pattern EXPIRY = Pattern.compile("^(0[1-9]|1[0-2])/[0-9]{4}$");

    private final CheckoutMapper checkoutMapper;

    private final CartCheckoutAccess cartCheckoutAccess;

    private final CatalogQuery catalogQuery;

    private final PricingQuery pricingQuery;

    private final Clock clock;

    public CheckoutApplicationService(CheckoutMapper checkoutMapper, CartCheckoutAccess cartCheckoutAccess,
            CatalogQuery catalogQuery, PricingQuery pricingQuery, Clock clock) {
        this.checkoutMapper = checkoutMapper;
        this.cartCheckoutAccess = cartCheckoutAccess;
        this.catalogQuery = catalogQuery;
        this.pricingQuery = pricingQuery;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CheckoutSnapshot create(CreateCheckoutRequest request) {
        CartSnapshot cart = cartCheckoutAccess.lockActiveCart(CartOwner.account(request.userId()),
                request.cartVersion());
        if (cart.lines().isEmpty()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "购物车为空，不能创建确认快照");
        }
        PaymentSummary payment = paymentSummary(request.payment());
        List<CheckoutLine> lines = new ArrayList<>();
        Money total = Money.zero();
        for (CartLine line : cart.lines()) {
            if (!pricingQuery.isListed(line.itemId())) {
                throw new BusinessFailure(BusinessFailure.Code.ITEM_UNAVAILABLE, "购物车包含已下架的商品");
            }
            Money price = pricingQuery.priceOf(line.itemId());
            if (!price.isPositive()) {
                throw new BusinessFailure(BusinessFailure.Code.ITEM_UNAVAILABLE, "购物车包含不可购买的商品");
            }
            Item item = catalogQuery.item(line.itemId())
                    .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.ITEM_UNAVAILABLE,
                            "购物车包含已删除的商品"));
            lines.add(new CheckoutLine(line.itemId(), line.quantity(), price, item.productName(),
                    item.attributes()));
            total = total.add(price.multiply(line.quantity()));
        }
        String checkoutId = UUID.randomUUID().toString();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(CheckoutModels.VALIDITY);
        checkoutMapper.insertCheckout(new CheckoutMapper.CheckoutRow(checkoutId, request.userId(),
                Long.valueOf(cart.cartId()), cart.version(), CheckoutMapper.STATE_OPEN, createdAt, expiresAt,
                total.toPlainString(),
                request.billingAddress().firstName(), request.billingAddress().lastName(),
                request.billingAddress().address1(), request.billingAddress().address2(),
                request.billingAddress().city(), request.billingAddress().state(), request.billingAddress().zip(),
                request.billingAddress().country(),
                request.shippingAddress().firstName(), request.shippingAddress().lastName(),
                request.shippingAddress().address1(), request.shippingAddress().address2(),
                request.shippingAddress().city(), request.shippingAddress().state(), request.shippingAddress().zip(),
                request.shippingAddress().country(),
                payment.cardType(), payment.lastFour()));
        int lineNumber = 1;
        for (CheckoutLine line : lines) {
            checkoutMapper.insertCheckoutItem(new CheckoutMapper.CheckoutItemRow(checkoutId, lineNumber++,
                    line.itemId(), line.quantity(), line.unitPrice().toPlainString(), line.productName(),
                    String.join(" / ", line.attributes())));
        }
        return new CheckoutSnapshot(checkoutId, request.userId(), cart.cartId(), cart.version(), createdAt,
                expiresAt, CheckoutState.OPEN, total, lines, request.billingAddress(), request.shippingAddress(),
                payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckoutSnapshot> findOwnCheckout(String userId, String checkoutId) {
        CheckoutMapper.CheckoutRow row = checkoutMapper.selectCheckout(checkoutId);
        if (row == null || !row.userId().equals(userId)) {
            return Optional.empty();
        }
        List<CheckoutLine> lines = new ArrayList<>();
        for (CheckoutMapper.CheckoutItemRow item : checkoutMapper.selectCheckoutItems(checkoutId)) {
            lines.add(new CheckoutLine(item.itemId(), item.quantity(), Money.cny(item.unitPrice()),
                    item.productName(), attributesOf(item.itemAttribute())));
        }
        return Optional.of(new CheckoutSnapshot(row.checkoutId(), row.userId(), String.valueOf(row.cartId()),
                row.cartVersion(), row.createdAt(), row.expiresAt(), CheckoutState.valueOf(row.state()),
                Money.cny(row.totalAmount()), lines, null, null,
                new PaymentSummary(row.cardType(), row.cardLastFour())));
    }


    /** 规格描述还原为列表；空描述表示没有规格。 */
    private List<String> attributesOf(String itemAttribute) {
        if (itemAttribute == null || itemAttribute.isBlank()) {
            return List.of();
        }
        return List.of(itemAttribute.split(" / "));
    }

    /** 模拟支付输入校验：只接受演示假卡号与 MM/YYYY 有效期（Q05、Q15）。 */
    private PaymentSummary paymentSummary(FakePayment payment) {
        if (payment == null) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "支付信息不能为空");
        }
        if (payment.cardType() == null || !List.of("Visa", "MasterCard", "American Express")
                .contains(payment.cardType())) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "不支持的卡片类型");
        }
        if (payment.cardNumber() == null || !payment.cardNumber().startsWith(FAKE_CARD_PREFIX)
                || payment.cardNumber().length() <= FAKE_CARD_PREFIX.length()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "只接受 FAKE- 开头的演示卡号");
        }
        if (payment.expiryDate() == null || !EXPIRY.matcher(payment.expiryDate()).matches()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "有效期格式必须是 MM/YYYY");
        }
        String digits = payment.cardNumber().substring(FAKE_CARD_PREFIX.length());
        String lastFour = digits.substring(Math.max(0, digits.length() - 4));
        return new PaymentSummary(payment.cardType(), lastFour);
    }
}
