package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.api.CartQuery;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.cart.infrastructure.SessionCartBinding;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * T286: 购物车 REST适配。
 *
 * <p>控制器只做协议适配：解析路径与请求体、解析当前持有权、调用应用端口、映射为 DTO。接口不接受
 * 客户端指定的任意 cartId——购物车不是可绕过的凭据，归属只来自当前会话或当前认证身份
 * （NFR-02、Q08）。写操作受 CSRF 保护，游客同样可以写自己的会话购物车（CRT-01）。</p>
 *
 * <p>所有写操作都携带 expectedVersion；版本不符返回 409 而不是静默覆盖其他标签页的修改
 * （plan §5.4）。</p>
 */
@RestController
public class CartController {

    private static final String VERSION = "1.0";

    private final CartCommand cartCommand;

    private final CartQuery cartQuery;

    private final CartAssembler assembler;

    private final SessionCartBinding sessionCartBinding;

    public CartController(CartCommand cartCommand, CartQuery cartQuery, CartAssembler assembler,
            SessionCartBinding sessionCartBinding) {
        this.cartCommand = cartCommand;
        this.cartQuery = cartQuery;
        this.assembler = assembler;
        this.sessionCartBinding = sessionCartBinding;
    }

    /** 当前购物车；无车返回空 DTO，不创建业务行。 */
    @GetMapping(path = "/api/cart", version = VERSION)
    public CartDtos.CartResponseDto cart(HttpServletRequest request, Authentication authentication) {
        CartSnapshot snapshot = cartQuery.currentCart(owner(request, authentication)).orElse(null);
        return snapshot == null ? emptyCart() : assembler.toCartResponse(snapshot);
    }

    /** 加购：同一可售项再次添加时数量加 1。 */
    @PostMapping(path = "/api/cart/items", version = VERSION)
    public CartDtos.CartResponseDto addItem(HttpServletRequest request, Authentication authentication,
            @Valid @RequestBody CartDtos.AddCartItemRequest body) {
        return assembler.toCartResponse(cartCommand.addItem(owner(request, authentication), body.itemId(),
                body.expectedVersion()));
    }

    /** 修改单项数量；数量小于 1 表示移除。 */
    @PutMapping(path = "/api/cart/items/{itemId}", version = VERSION)
    public CartDtos.CartResponseDto setQuantity(HttpServletRequest request, Authentication authentication,
            @PathVariable("itemId") String itemId, @Valid @RequestBody CartDtos.CartQuantityRequest body) {
        return assembler.toCartResponse(cartCommand.setQuantity(owner(request, authentication), itemId,
                body.quantity(), body.expectedVersion()));
    }

    /** 整批数量更新：先校验整批，非法时不部分写入。 */
    @PutMapping(path = "/api/cart/items", version = VERSION)
    public CartDtos.CartResponseDto applyQuantities(HttpServletRequest request, Authentication authentication,
            @Valid @RequestBody CartDtos.CartBatchRequest body) {
        List<CartCommand.QuantityChange> changes = body.items().stream()
                .map(entry -> new CartCommand.QuantityChange(entry.itemId(), entry.quantity()))
                .toList();
        return assembler.toCartResponse(cartCommand.applyQuantities(owner(request, authentication), changes,
                body.expectedVersion()));
    }

    /** 移除某个可售项；移除不存在的项不影响其他内容。 */
    @DeleteMapping(path = "/api/cart/items/{itemId}", version = VERSION)
    public CartDtos.CartResponseDto removeItem(HttpServletRequest request, Authentication authentication,
            @PathVariable("itemId") String itemId,
            @RequestParam(name = "expectedVersion") long expectedVersion) {
        return assembler.toCartResponse(cartCommand.removeItem(owner(request, authentication), itemId,
                expectedVersion));
    }

    /** 登录合并超限后，用户调整账号购物车后显式重试；仍超限返回 409 且不丢数据。 */
    @PostMapping(path = "/api/cart/merge", version = VERSION)
    public CartDtos.CartResponseDto merge(HttpServletRequest request, Authentication authentication,
            @Valid @RequestBody CartDtos.CartMergeRequest body) {
        return assembler.toCartResponse(cartCommand.mergeGuestCart(owner(request, authentication),
                body.expectedVersion()));
    }

    private CartOwner owner(HttpServletRequest request, Authentication authentication) {
        return sessionCartBinding.currentOwner(request, username(authentication));
    }

    private String username(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }

    /** 空购物车响应：无业务行时返回明确空 DTO，而不是 null。 */
    private CartDtos.CartResponseDto emptyCart() {
        return new CartDtos.CartResponseDto(0L, 0, 0, List.of(), "0.00", "CNY", false, List.of());
    }
}
