package ik.am.jpetstore.catalog.web;

import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.shared.domain.Money;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * T310: catalog 显式 DTO/领域转换。
 *
 * <p>逐字段映射；金额一律变成两位小数的十进制字符串，绝不变成 JSON 数字，避免前端精度丢失
 * （plan §6.1、Q06）。商品文本原样传递，不做任何 HTML 处理或富文本包装（NFR-05、D07）。</p>
 */
@Component
public class DefaultCatalogAssembler implements CatalogAssembler {

    @Override
    public CatalogDtos.CategoryDto toCategory(Category category) {
        return new CatalogDtos.CategoryDto(category.categoryId(), category.name(), category.description());
    }

    @Override
    public CatalogDtos.ProductDto toProduct(Product product) {
        return new CatalogDtos.ProductDto(product.productId(), product.categoryId(), product.name(),
                product.description(), product.imagePath());
    }

    @Override
    public CatalogDtos.ItemDto toItem(Item item) {
        return new CatalogDtos.ItemDto(item.itemId(), item.productId(), item.productName(), item.attributes(),
                money(item.unitPrice()), item.inStock(), item.availableQuantity(), item.purchasable());
    }

    @Override
    public CatalogDtos.ProductDetailDto toProductDetail(ProductDetail detail) {
        List<CatalogDtos.ItemDto> items = detail.items().stream().map(this::toItem).toList();
        return new CatalogDtos.ProductDetailDto(toProduct(detail.product()), items);
    }

    @Override
    public CatalogDtos.BannerDto toBanner(Banner banner) {
        return new CatalogDtos.BannerDto(banner.imagePath(), banner.description());
    }

    private String money(Money money) {
        return money.toPlainString();
    }
}
