package ik.am.jpetstore.catalog.web;

import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;

/**
 * T209: catalog 显式 DTO/领域转换接口。
 *
 * <p>只声明签名；金额字符串化由真实转换器负责（Phase 7）。</p>
 */
public interface CatalogAssembler {

    CatalogDtos.CategoryDto toCategory(Category category);

    CatalogDtos.ProductDto toProduct(Product product);

    CatalogDtos.ItemDto toItem(Item item);

    CatalogDtos.ProductDetailDto toProductDetail(ProductDetail detail);

    CatalogDtos.BannerDto toBanner(Banner banner);
}
