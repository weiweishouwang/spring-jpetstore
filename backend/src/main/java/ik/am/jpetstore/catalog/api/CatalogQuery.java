package ik.am.jpetstore.catalog.api;

import java.util.List;
import java.util.Optional;

import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;

/**
 * T193: 商品及偏好展示查询端口。
 *
 * <p>只读公开目录：分类、产品、可售项、横幅。搜索只按产品名称整体包含匹配、不区分大小写，
 * 不引入分词或描述匹配（CAT-05、CAT-07、Q14）。横幅缺失只隐藏该区域，不影响账户认证
 * （PER-03、NFR-02）。</p>
 */
public interface CatalogQuery {

    /** 全部分类。 */
    List<Category> categories();

    /** 分类下的产品列表。 */
    List<Product> productsByCategory(String categoryId);

    /** 产品详情（含可售项）；不存在时返回空。 */
    Optional<ProductDetail> product(String productId);

    /** 可售项详情；不存在时返回空。 */
    Optional<Item> item(String itemId);

    /** 按产品名称包含匹配（大小写不敏感）；关键词为空时由调用方拒绝。 */
    List<Product> searchByName(String keyword);

    /** 偏好分类下的产品列表（My List，PER-01）。 */
    List<Product> productsOfCategory(String categoryId);

    /** 偏好分类的横幅；缺失时返回空。 */
    Optional<Banner> banner(String categoryId);
}
