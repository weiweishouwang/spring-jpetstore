package ik.am.jpetstore.cart.application;

import ik.am.jpetstore.cart.api.CartCheckoutAccess;
import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.api.CartQuery;
import ik.am.jpetstore.cart.domain.CartModels.CartLine;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.cart.domain.CartModels.CartState;
import ik.am.jpetstore.cart.domain.CartModels.PendingMerge;
import ik.am.jpetstore.cart.infrastructure.CartMapper;
import ik.am.jpetstore.catalog.api.PricingQuery;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * T284: 会话购物车命令与查询服务。
 *
 * <p>购物车在数据库里：游客车按会话绑定哈希定位，账号车按账号定位并跨设备保留（Q08）。所有
 * 变更都在应用服务事务内锁住购物车，校验持有权与 expectedVersion 之后才写明细并递增版本
 * （plan §5.4）。数量为整数：小于 1 表示移除，0 与负数都不走删除之外的路径；批量更新先完整
 * 校验再写入，非法输入不做半批更新（CRT-06～08、Q15）。</p>
 *
 * <p>加购不扣库存：购物车的“有货”只是参考，库存只在成交时校验（CRT-04、CRT-05、INV-02）。
 * 下架商品不可加购，缺货商品允许加购（Q02、Q10）。</p>
 *
 * <p>登录合并在读取路径上自动进行：同一个会话里的游客车并入账号车，同 SKU 数量相加；合并后
 * 数量超过上限时冻结保存为待合并内容，双方内容都不丢弃，等待账号调整后显式重试（Q08）。</p>
 */
@Service
public class CartApplicationService implements CartCommand, CartQuery, CartCheckoutAccess {

    /** 单 SKU 数量上限（Q15）。 */
    public static final int MAX_QUANTITY = 9999;

    /** 游客购物车有效期：30 分钟无活动失效（Q08）。账号车保留至成交或手动移除。 */
    public static final Duration GUEST_CART_VALIDITY = Duration.ofMinutes(30);

    private final CartMapper mapper;

    private final PricingQuery pricingQuery;

    private final Clock clock;

    public CartApplicationService(CartMapper mapper, PricingQuery pricingQuery, Clock clock) {
        this.mapper = mapper;
        this.pricingQuery = pricingQuery;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 读取

    @Override
    @Transactional
    public Optional<CartSnapshot> currentCart(CartOwner owner) {
        mergeGuestCartInto(owner, null);
        CartMapper.CartRow cart = findActiveCart(owner);
        return cart == null ? Optional.empty() : Optional.of(snapshot(owner, cart));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartSnapshot> findOwnedCart(CartOwner owner, String cartId) {
        CartMapper.CartRow cart = findActiveCart(owner);
        if (cart == null || cartId == null || !String.valueOf(cart.cartId()).equals(cartId)) {
            return Optional.empty();
        }
        return Optional.of(snapshot(owner, cart));
    }

    // ------------------------------------------------------------------ 命令

    @Override
    @Transactional
    public CartSnapshot addItem(CartOwner owner, String itemId, long expectedVersion) {
        requireListed(itemId);
        return mutate(owner, expectedVersion, quantities -> {
            int current = quantities.getOrDefault(itemId, 0);
            int updated = current + 1;
            if (updated > MAX_QUANTITY) {
                throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED,
                        "单个商品的数量不能超过 " + MAX_QUANTITY);
            }
            quantities.put(itemId, updated);
        });
    }

    @Override
    @Transactional
    public CartSnapshot setQuantity(CartOwner owner, String itemId, int quantity, long expectedVersion) {
        requireListed(itemId);
        validateQuantity(itemId, quantity);
        return mutate(owner, expectedVersion, quantities -> {
            if (quantity < 1) {
                quantities.remove(itemId);
            } else {
                quantities.put(itemId, quantity);
            }
        });
    }

    @Override
    @Transactional
    public CartSnapshot removeItem(CartOwner owner, String itemId, long expectedVersion) {
        return mutate(owner, expectedVersion, quantities -> quantities.remove(itemId));
    }

    @Override
    @Transactional
    public CartSnapshot applyQuantities(CartOwner owner, List<QuantityChange> changes, long expectedVersion) {
        Map<String, Integer> validated = new LinkedHashMap<>();
        List<BusinessFailure.Violation> violations = new ArrayList<>();
        for (QuantityChange change : changes) {
            if (change == null || change.itemId() == null || change.itemId().isBlank()) {
                violations.add(new BusinessFailure.Violation("items", "商品编号不能为空"));
                continue;
            }
            if (change.quantity() > MAX_QUANTITY) {
                violations.add(new BusinessFailure.Violation("items", "单个商品的数量不能超过 " + MAX_QUANTITY));
                continue;
            }
            if (change.quantity() < 0) {
                violations.add(new BusinessFailure.Violation("items", "数量不能为负数"));
                continue;
            }
            if (validated.containsKey(change.itemId())) {
                violations.add(new BusinessFailure.Violation("items", "同一商品不能重复出现"));
                continue;
            }
            validated.put(change.itemId(), change.quantity());
        }
        if (!violations.isEmpty()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "批量数量更新不合法", violations);
        }
        return mutate(owner, expectedVersion, quantities -> {
            for (Map.Entry<String, Integer> entry : validated.entrySet()) {
                if (entry.getValue() < 1) {
                    quantities.remove(entry.getKey());
                } else {
                    quantities.put(entry.getKey(), entry.getValue());
                }
            }
        });
    }

    @Override
    @Transactional
    public CartSnapshot mergeGuestCart(CartOwner owner, long expectedVersion) {
        MergeOutcome outcome = mergeGuestCartInto(owner, expectedVersion);
        if (outcome == MergeOutcome.CONFLICT) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "合并后数量超过上限，请调整购物车后重试");
        }
        CartMapper.CartRow cart = findActiveCart(owner);
        return cart == null ? emptySnapshot(owner) : snapshot(owner, cart);
    }

    // ------------------------------------------------------- 订单事务内访问

    @Override
    @Transactional
    public CartSnapshot lockActiveCart(CartOwner owner, long expectedVersion) {
        CartMapper.CartRow cart = lockActiveCartRow(owner);
        if (isExpired(cart)) {
            cart = null;
        }
        long currentVersion = cart == null ? 0 : cart.version();
        if (currentVersion != expectedVersion) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        return cart == null ? emptySnapshot(owner) : snapshot(owner, cart);
    }

    @Override
    @Transactional
    public void consume(CartOwner owner, String cartId, long version) {
        CartMapper.CartRow cart = lockActiveCartRow(owner);
        if (cart == null || !String.valueOf(cart.cartId()).equals(cartId)) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已不可用，无法完成本次成交");
        }
        if (cart.version() != version) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        mapper.updateState(cart.cartId(), CartMapper.STATE_CONSUMED);
    }

    // ------------------------------------------------------------ 内部实现

    /** 明细变更：输入当前数量表，输出期望数量表。 */
    @FunctionalInterface
    private interface QuantityChangeApplier {
        void apply(Map<String, Integer> quantities);
    }

    /** 合并结果。 */
    private enum MergeOutcome {
        /** 没有可合并的游客车。 */
        NOTHING,
        /** 合并成功。 */
        MERGED,
        /** 合并后超过上限，已冻结为待合并内容。 */
        CONFLICT
    }

    private CartSnapshot mutate(CartOwner owner, long expectedVersion, QuantityChangeApplier change) {
        CartMapper.CartRow cart = lockActiveCartRow(owner);
        boolean expired = isExpired(cart);
        long currentVersion = cart == null || expired ? 0 : cart.version();
        if (currentVersion != expectedVersion) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        if (cart == null) {
            cart = createCart(owner);
        } else if (expired) {
            mapper.updateState(cart.cartId(), CartMapper.STATE_EXPIRED);
            cart = createCart(owner);
        }
        Map<String, Integer> current = new LinkedHashMap<>();
        for (CartMapper.CartItemRow row : mapper.selectCartItems(cart.cartId())) {
            current.put(row.itemId(), row.quantity());
        }
        Map<String, Integer> desired = new LinkedHashMap<>(current);
        change.apply(desired);

        for (Map.Entry<String, Integer> entry : desired.entrySet()) {
            Integer previous = current.get(entry.getKey());
            if (previous == null) {
                mapper.insertCartItem(new CartMapper.CartItemRow(cart.cartId(), entry.getKey(), entry.getValue()));
            } else if (!previous.equals(entry.getValue())) {
                mapper.updateCartItemQuantity(cart.cartId(), entry.getKey(), entry.getValue());
            }
        }
        for (String itemId : current.keySet()) {
            if (!desired.containsKey(itemId)) {
                mapper.deleteCartItem(cart.cartId(), itemId);
            }
        }
        if (mapper.bumpVersion(cart.cartId(), cart.version(), expiresAt(owner)) != 1) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        CartMapper.CartRow updated = new CartMapper.CartRow(cart.cartId(), cart.sessionBindingHash(),
                cart.ownerUserId(), cart.version() + 1, cart.state(), cart.createdAt(), expiresAt(owner));
        return snapshot(owner, updated);
    }

    /**
     * 登录合并：把同一会话里的游客车并入账号车。
     *
     * @param expectedVersion 账号车的期望版本；为 null 表示自动合并（读取路径），不做版本校验
     * @return 合并结果
     */
    private MergeOutcome mergeGuestCartInto(CartOwner owner, Long expectedVersion) {
        if (owner.isAnonymous() || owner.sessionBindingHash() == null) {
            return MergeOutcome.NOTHING;
        }
        CartMapper.CartRow guestCart = mapper.selectActiveCartBySessionForUpdate(owner.sessionBindingHash());
        if (guestCart == null || isExpired(guestCart)) {
            return MergeOutcome.NOTHING;
        }
        CartMapper.CartRow accountCart = mapper.selectActiveCartByOwnerForUpdate(owner.userId());
        boolean accountExpired = isExpired(accountCart);
        long accountVersion = accountCart == null || accountExpired ? 0 : accountCart.version();
        if (expectedVersion != null && expectedVersion != accountVersion) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        if (accountCart == null || accountExpired) {
            if (accountExpired) {
                mapper.updateState(accountCart.cartId(), CartMapper.STATE_EXPIRED);
            }
            mapper.transferToAccount(guestCart.cartId(), owner.userId());
            return MergeOutcome.MERGED;
        }
        Map<String, Integer> accountQuantities = new LinkedHashMap<>();
        for (CartMapper.CartItemRow row : mapper.selectCartItems(accountCart.cartId())) {
            accountQuantities.put(row.itemId(), row.quantity());
        }
        Map<String, Integer> guestQuantities = new LinkedHashMap<>();
        for (CartMapper.CartItemRow row : mapper.selectCartItems(guestCart.cartId())) {
            guestQuantities.put(row.itemId(), row.quantity());
        }
        Map<String, Integer> merged = new LinkedHashMap<>(accountQuantities);
        for (Map.Entry<String, Integer> entry : guestQuantities.entrySet()) {
            int quantity = merged.getOrDefault(entry.getKey(), 0) + entry.getValue();
            if (quantity > MAX_QUANTITY) {
                mapper.freezeAsPendingMerge(guestCart.cartId(), owner.userId());
                return MergeOutcome.CONFLICT;
            }
            merged.put(entry.getKey(), quantity);
        }
        for (Map.Entry<String, Integer> entry : guestQuantities.entrySet()) {
            Integer existing = accountQuantities.get(entry.getKey());
            if (existing == null) {
                mapper.insertCartItem(
                        new CartMapper.CartItemRow(accountCart.cartId(), entry.getKey(), entry.getValue()));
            } else {
                mapper.updateCartItemQuantity(accountCart.cartId(), entry.getKey(), merged.get(entry.getKey()));
            }
        }
        mapper.deleteCart(guestCart.cartId());
        mapper.bumpVersion(accountCart.cartId(), accountCart.version(), null);
        return MergeOutcome.MERGED;
    }

    private CartMapper.CartRow createCart(CartOwner owner) {
        Instant now = now();
        CartMapper.NewCart cart = new CartMapper.NewCart(
                owner.isAnonymous() ? owner.sessionBindingHash() : null,
                owner.isAnonymous() ? null : owner.userId(),
                0L, CartMapper.STATE_ACTIVE, now, expiresAt(owner));
        try {
            mapper.insertCart(cart);
        } catch (DuplicateKeyException e) {
            throw new BusinessFailure(BusinessFailure.Code.CART_VERSION_CONFLICT,
                    "购物车已被其他操作修改，请刷新后重试");
        }
        return new CartMapper.CartRow(cart.getCartId(), cart.getSessionBindingHash(), cart.getOwnerUserId(), 0L,
                CartMapper.STATE_ACTIVE, now, cart.getExpiresAt());
    }

    private CartSnapshot snapshot(CartOwner owner, CartMapper.CartRow cart) {
        List<CartLine> lines = new ArrayList<>();
        Money subtotal = Money.zero();
        for (CartMapper.CartItemRow row : mapper.selectCartItems(cart.cartId())) {
            Money price = priceOf(row.itemId());
            boolean inStock = price.isPositive() && availableQuantity(row.itemId()) > 0;
            if (price.isPositive()) {
                subtotal = subtotal.add(price.multiply(row.quantity()));
            }
            lines.add(new CartLine(row.itemId(), row.quantity(), inStock));
        }
        return new CartSnapshot(String.valueOf(cart.cartId()), owner, cart.version(), CartState.ACTIVE, lines,
                subtotal, pendingMerges(owner));
    }

    private CartSnapshot emptySnapshot(CartOwner owner) {
        return new CartSnapshot(null, owner, 0L, CartState.ACTIVE, List.of(), Money.zero(), pendingMerges(owner));
    }

    private List<PendingMerge> pendingMerges(CartOwner owner) {
        if (owner.isAnonymous() || owner.userId() == null) {
            return List.of();
        }
        Map<String, List<CartLine>> grouped = new LinkedHashMap<>();
        for (CartMapper.PendingMergeRow row : mapper.selectPendingMerges(owner.userId())) {
            grouped.computeIfAbsent(String.valueOf(row.mergeId()), key -> new ArrayList<>())
                    .add(new CartLine(row.itemId(), row.quantity(), false));
        }
        return grouped.entrySet().stream()
                .map(entry -> new PendingMerge(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private CartMapper.CartRow findActiveCart(CartOwner owner) {
        CartMapper.CartRow cart = owner.isAnonymous()
                ? mapper.selectActiveCartBySession(owner.sessionBindingHash())
                : mapper.selectActiveCartByOwner(owner.userId());
        return isExpired(cart) ? null : cart;
    }

    private CartMapper.CartRow lockActiveCartRow(CartOwner owner) {
        return owner.isAnonymous()
                ? mapper.selectActiveCartBySessionForUpdate(owner.sessionBindingHash())
                : mapper.selectActiveCartByOwnerForUpdate(owner.userId());
    }

    private boolean isExpired(CartMapper.CartRow cart) {
        return cart != null && cart.expiresAt() != null && !cart.expiresAt().isAfter(now());
    }

    private Instant expiresAt(CartOwner owner) {
        return owner.isAnonymous() ? now().plus(GUEST_CART_VALIDITY) : null;
    }

    private void requireListed(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "商品编号不能为空");
        }
        if (!pricingQuery.isListed(itemId)) {
            throw new BusinessFailure(BusinessFailure.Code.ITEM_UNAVAILABLE, "商品已下架，不能加购");
        }
    }

    private void validateQuantity(String itemId, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED,
                    "单个商品的数量不能超过 " + MAX_QUANTITY);
        }
        if (quantity < 0) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "数量不能为负数");
        }
    }

    private Money priceOf(String itemId) {
        try {
            return pricingQuery.priceOf(itemId);
        } catch (BusinessFailure e) {
            if (e.code() == BusinessFailure.Code.RESOURCE_NOT_FOUND) {
                return Money.zero();
            }
            throw e;
        }
    }

    private int availableQuantity(String itemId) {
        try {
            return pricingQuery.availableQuantity(itemId);
        } catch (BusinessFailure e) {
            if (e.code() == BusinessFailure.Code.RESOURCE_NOT_FOUND) {
                return 0;
            }
            throw e;
        }
    }

    private Instant now() {
        return clock.instant();
    }
}
