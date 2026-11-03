package ik.am.jpetstore.catalog.web;

import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T309: catalog 显式 DTO/领域转换。
 *
 * <p>验证金额一律变成十进制字符串（避免 JavaScript 精度丢失）、商品文本原样传递、规格按顺序
 * 保留、横幅只含受控图片引用与纯文本（plan §6.1、NFR-05）。</p>
 */
class DefaultCatalogAssemblerTest {

    private final DefaultCatalogAssembler assembler = new DefaultCatalogAssembler();

    @Test
    void theCategoryIsMappedFieldByField() {
        CatalogDtos.CategoryDto dto = assembler.toCategory(new Category("FISH", "Fish", "Saltwater friends"));

        assertThat(dto.categoryId()).isEqualTo("FISH");
        assertThat(dto.name()).isEqualTo("Fish");
        assertThat(dto.description()).isEqualTo("Saltwater friends");
    }

    @Test
    void theProductKeepsItsOptionalImagePath() {
        CatalogDtos.ProductDto dto = assembler.toProduct(new Product("FI-SW-01", "FISH", "Angelfish", "d", null));

        assertThat(dto.productId()).isEqualTo("FI-SW-01");
        assertThat(dto.categoryId()).isEqualTo("FISH");
        assertThat(dto.imagePath()).isNull();
    }

    @Test
    void theItemPriceBecomesATwoDecimalString() {
        CatalogDtos.ItemDto dto = assembler.toItem(new Item("EST-1", "FI-SW-01", "Angelfish", List.of("Large"),
                Money.cny("16.50"), 10, true, true));

        assertThat(dto.unitPrice())
                .as("金额是十进制字符串，不是 JSON 数字（plan §6.1）")
                .isEqualTo("16.50");
        assertThat(dto.attributes()).containsExactly("Large");
        assertThat(dto.availableQuantity()).isEqualTo(10);
        assertThat(dto.purchasable()).isTrue();
    }

    @Test
    void aWholeAmountKeepsItsTwoDecimals() {
        CatalogDtos.ItemDto dto = assembler.toItem(new Item("EST-9", "FI-SW-01", "Angelfish", List.of(),
                Money.cny("7"), 0, false, false));

        assertThat(dto.unitPrice()).isEqualTo("7.00");
        assertThat(dto.inStock()).isFalse();
    }

    @Test
    void theProductDetailCarriesItsItems() {
        CatalogDtos.ProductDetailDto dto = assembler.toProductDetail(new ProductDetail(
                new Product("FI-SW-01", "FISH", "Angelfish", "d", null),
                List.of(new Item("EST-1", "FI-SW-01", "Angelfish", List.of("Large"), Money.cny("16.50"), 10, true,
                        true))));

        assertThat(dto.product().productId()).isEqualTo("FI-SW-01");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).itemId()).isEqualTo("EST-1");
        assertThat(dto.items().get(0).unitPrice()).isEqualTo("16.50");
    }

    @Test
    void theBannerCarriesOnlyAPathAndPlainText() {
        CatalogDtos.BannerDto dto = assembler.toBanner(new Banner("/resources/images/banner_fish.gif", "Fish banner"));

        assertThat(dto.imagePath()).isEqualTo("/resources/images/banner_fish.gif");
        assertThat(dto.description()).isEqualTo("Fish banner");
    }
}
