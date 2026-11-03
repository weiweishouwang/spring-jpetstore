package ik.am.jpetstore.catalog.infrastructure;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * T243: Catalog Mapper签名与类型约定。
 *
 * <p>目录查询是只读投影：可售项视图带当前库存，商品查询不写库存（plan §3.2）。搜索只按产品
 * 名称整体包含匹配，% 与 _ 按字面转义（Q14）。SQL 定义在 mapper/catalog/CatalogMapper.xml（T245）。</p>
 */
@Mapper
public interface CatalogMapper {

    /** 分类行。 */
    record CategoryRow(String categoryId, String name, String description) {
    }

    /** 产品行。 */
    record ProductRow(String productId, String categoryId, String name, String description, String imagePath) {
    }

    /**
     * 可售项行（含当前库存与商品状态）。
     *
     * @param availableQuantity 当前可用库存；缺货可加购但不得成交（Q02）
     * @param status            商品状态；P 表示在售，下架商品不可加购也不可成交（Q10）
     */
    record ItemRow(String itemId, String productId, String productName, String listPrice, String attribute1,
            String attribute2, String attribute3, String attribute4, String attribute5,
            Integer availableQuantity, String status) {
    }

    /** 横幅行；横幅缺失时查询返回空。 */
    record BannerRow(String favCategory, String bannerName) {
    }

    List<CategoryRow> selectCategories();

    List<ProductRow> selectProductsByCategory(String categoryId);

    ProductRow selectProduct(String productId);

    List<ItemRow> selectItemsByProduct(String productId);

    ItemRow selectItem(String itemId);

    List<ProductRow> selectProductsByName(String keyword);

    BannerRow selectBanner(String favCategory);
}
