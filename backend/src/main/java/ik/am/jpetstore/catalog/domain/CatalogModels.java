package ik.am.jpetstore.catalog.domain;

import java.util.List;

import ik.am.jpetstore.shared.domain.Money;

/**
 * T185: 分类、产品、可售项及受控展示数据类型。
 *
 * <p>展示文本一律为纯文本或受控静态资源引用，不携带可执行 HTML（D07、NFR-05）。
 * 库存与"可售"状态由只读投影查询得到，catalog 不写库存（见 plan §3.2）。</p>
 */
public final class CatalogModels {

    private CatalogModels() {
    }

    /** 商品分类。 */
    public record Category(String categoryId, String name, String description) {
    }

    /** 产品：分类下的展示分组，本身不是购买单位。 */
    public record Product(String productId, String categoryId, String name, String description, String imagePath) {
    }

    /**
     * 可售项（SKU）：购买单位。
     *
     * @param availableQuantity 当前可用库存；缺货可加购但不得成交（Q02）
     * @param inStock           可用数量大于零（INV-01）
     * @param purchasable       商品状态允许成交（Q10：下架商品不可加购、不可成交）
     */
    public record Item(String itemId, String productId, String productName, List<String> attributes,
            Money unitPrice, int availableQuantity, boolean inStock, boolean purchasable) {
    }

    /** 产品详情：产品 + 其可售项。 */
    public record ProductDetail(Product product, List<Item> items) {
    }

    /** 横幅：受控图片引用 + 纯文本说明；缺失时以空值表达。 */
    public record Banner(String imagePath, String description) {
    }
}
