package ik.am.jpetstore.cart.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * T211: cart HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：金额为十进制字符串；数量小于 1 表示移除、正数上限 9999
 * （Q15）；version 是并发控制字段，冲突返回 409 而不静默覆盖（plan §5.4）。可售项视图在本模块
 * 独立定义，字段与契约 Item 一致，避免模块之间直接依赖对方的 Web 类型。</p>
 */
public final class CartDtos {

    private CartDtos() {
    }

    /** 可售项视图（契约 Item）。 */
    public record ItemView(
            @NotBlank @Size(max = 10) String itemId,
            @NotBlank @Size(max = 10) String productId,
            @NotBlank @Size(max = 80) String productName,
            List<String> attributes,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,7})\\.[0-9]{2}$") String unitPrice,
            boolean inStock,
            @PositiveOrZero int availableQuantity,
            boolean purchasable) {
    }

    /** 购物车明细：数量小于 1 不出现在响应中（已按移除语义处理）。 */
    public record CartItemDto(@NotNull @Valid ItemView item, @Max(9999) int quantity,
            @NotBlank String lineAmount) {
    }

    /** 待合并内容：登录合并超限时冻结保存，双方内容都不丢弃（Q08）。 */
    public record PendingMergeDto(@NotBlank @Size(max = 128) String mergeId, List<CartItemDto> items) {
    }

    /**
     * 购物车响应。
     *
     * @param version       购物车版本
     * @param lineCount     行数（与商品数量总和区分，CRT-09）
     * @param totalQuantity 商品数量总和
     * @param mergeConflict 登录合并是否因超限待处理
     */
    public record CartResponseDto(
            @PositiveOrZero long version,
            @PositiveOrZero int lineCount,
            @PositiveOrZero int totalQuantity,
            List<CartItemDto> items,
            @NotBlank String subtotal,
            @Pattern(regexp = "CNY") String currency,
            boolean mergeConflict,
            List<PendingMergeDto> pendingMerges) {
    }

    /** 加购请求：每次调用加 1，不自动重试。 */
    public record AddCartItemRequest(@NotBlank @Size(max = 10) String itemId, @PositiveOrZero long expectedVersion) {
    }

    /** 登录合并游客车请求。 */
    public record CartMergeRequest(@PositiveOrZero long expectedVersion) {
    }

    /** 单项数量更新请求：quantity 小于 1 表示移除。 */
    public record CartQuantityRequest(@Max(9999) int quantity, @PositiveOrZero long expectedVersion) {
    }

    /** 批量更新中的单项。 */
    public record CartQuantityEntry(@NotBlank @Size(max = 10) String itemId, @Max(9999) int quantity) {
    }

    /** 批量数量更新请求：先校验整批，非法时不部分写入。 */
    public record CartBatchRequest(@PositiveOrZero long expectedVersion, @NotEmpty List<@Valid CartQuantityEntry> items) {
    }
}
