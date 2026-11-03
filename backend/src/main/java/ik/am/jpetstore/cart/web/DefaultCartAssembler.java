package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.domain.CartModels.CartLine;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.cart.domain.CartModels.PendingMerge;
import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * T312: cart 显式 DTO/领域转换。
 *
 * <p>权威金额来自服务端快照：明细金额 = 服务端单价 × 数量，前端不重算（plan §5.4）。行数与商品
 * 数量总和分别统计（CRT-09）。待合并内容只展示当前账号自己持有的部分（Q08）。</p>
 *
 * <p>可售项视图取自目录的只读查询：购物车模块不自己读商品表（plan §3.2）。</p>
 */
@Component
public class DefaultCartAssembler implements CartAssembler {

    private final CatalogQuery catalogQuery;

    public DefaultCartAssembler(CatalogQuery catalogQuery) {
        this.catalogQuery = catalogQuery;
    }

    @Override
    public CartDtos.CartResponseDto toCartResponse(CartSnapshot snapshot) {
        List<CartDtos.CartItemDto> items = snapshot.lines().stream().map(this::toCartItem).toList();
        List<CartDtos.PendingMergeDto> pending = snapshot.pendingMerges().stream()
                .map(this::toPendingMerge)
                .toList();
        return new CartDtos.CartResponseDto(snapshot.version(), snapshot.lineCount(), snapshot.totalQuantity(),
                items, snapshot.subtotal().toPlainString(), snapshot.subtotal().currency().getCurrencyCode(),
                !pending.isEmpty(), pending);
    }

    @Override
    public CartDtos.ItemView toItemView(String itemId) {
        Item item = catalogQuery.item(itemId)
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "可售项不存在"));
        return new CartDtos.ItemView(item.itemId(), item.productId(), item.productName(), item.attributes(),
                item.unitPrice().toPlainString(), item.inStock(), item.availableQuantity(), item.purchasable());
    }

    private CartDtos.CartItemDto toCartItem(CartLine line) {
        CartDtos.ItemView item = toItemView(line.itemId());
        Money lineAmount = Money.cny(item.unitPrice()).multiply(line.quantity());
        return new CartDtos.CartItemDto(item, line.quantity(), lineAmount.toPlainString());
    }

    private CartDtos.PendingMergeDto toPendingMerge(PendingMerge merge) {
        List<CartDtos.CartItemDto> items = merge.items().stream().map(this::toCartItem).toList();
        return new CartDtos.PendingMergeDto(merge.mergeId(), items);
    }
}
