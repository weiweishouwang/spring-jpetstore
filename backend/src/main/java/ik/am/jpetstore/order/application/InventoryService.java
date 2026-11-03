package ik.am.jpetstore.order.application;

import ik.am.jpetstore.order.infrastructure.InventoryMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * T290: 同事务库存条件扣减。
 *
 * <p>库存由 order 模块负责变更（plan §3.2）。扣减是条件更新：只有当前库存足够时影响行数才是 1；
 * 不足或商品不存在时影响行数为 0，翻译成明确的业务失败并让整个事务回滚（Q02、INV-01～03、
 * D02）。扣减按 itemid 升序执行，全局锁顺序一致以减少死锁（plan §5.6）。</p>
 *
 * <p>本服务不开启独立提交：调用方（下单事务）的事务边界是唯一边界，失败时库存与订单一起回滚
 * （NFR-01、ORD-12）。</p>
 */
@Service
public class InventoryService {

    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * 扣减一批库存。
     *
     * @param deductions 扣减项；数量必须为正
     * @throws BusinessFailure 数量非法（400）或库存不足（409）时抛出，调用方事务整体回滚
     */
    @Transactional
    public void deduct(List<StockDeduction> deductions) {
        List<StockDeduction> ordered = deductions.stream()
                .sorted(Comparator.comparing(StockDeduction::itemId))
                .toList();
        for (StockDeduction deduction : ordered) {
            if (deduction.quantity() <= 0) {
                throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "扣减数量必须为正");
            }
            if (inventoryMapper.decrementStock(deduction.itemId(), deduction.quantity()) != 1) {
                throw new BusinessFailure(BusinessFailure.Code.OUT_OF_STOCK, "商品库存不足，无法完成本次成交");
            }
        }
    }

    /**
     * 库存扣减项。
     *
     * @param quantity 正整数；成交数量必须为正（Q15）
     */
    public record StockDeduction(String itemId, int quantity) {
    }
}
