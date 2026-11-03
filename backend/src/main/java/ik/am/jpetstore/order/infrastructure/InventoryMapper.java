package ik.am.jpetstore.order.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * T272: Inventory Mapper签名与类型约定。
 *
 * <p>库存由 order 模块负责变更，catalog 只读投影不写库存（plan §3.2）。扣减是条件更新：
 * 只有当前库存足够时才成功，返回的影响行数必须是 1；不足或不存在时影响行数为 0，由应用层
 * 翻译成明确的业务失败并整体回滚（Q02、INV-01～03、D02）。禁止任何会放大库存的写法。</p>
 */
@Mapper
public interface InventoryMapper {

    /** inventory 表行。 */
    record InventoryRow(String itemId, Integer quantity, Long version) {
    }

    /** 读取库存行；不存在时返回 null。 */
    InventoryRow selectInventory(@Param("itemId") String itemId);

    /**
     * 条件扣减库存。
     *
     * @param quantity 扣减数量；必须为正，由应用层校验
     * @return 影响行数；1 表示扣减成功，0 表示库存不足或商品不存在
     */
    int decrementStock(@Param("itemId") String itemId, @Param("quantity") int quantity);
}
