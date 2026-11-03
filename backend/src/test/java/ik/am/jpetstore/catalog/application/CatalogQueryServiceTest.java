package ik.am.jpetstore.catalog.application;

import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.catalog.infrastructure.CatalogMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T246: 商品和偏好展示查询服务。
 *
 * <p>覆盖目录层级、空结果、名称搜索范围、缺失资源、横幅缺失与实时价格；服务只读不写库存
 * （CAT-01～08、PER-01～03）。</p>
 */
class CatalogQueryServiceTest {

    private CatalogMapper mapper;

    private CatalogQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CatalogMapper.class);
        service = new CatalogQueryService(mapper);
    }

    @Test
    void listsCategories() {
        when(mapper.selectCategories()).thenReturn(List.of(
                new CatalogMapper.CategoryRow("FISH", "Fish", "Saltwater friends")));

        List<Category> categories = service.categories();

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).categoryId()).isEqualTo("FISH");
        assertThat(categories.get(0).name()).isEqualTo("Fish");
    }

    @Test
    void listsProductsOfACategory() {
        when(mapper.selectProductsByCategory("FISH")).thenReturn(List.of(
                new CatalogMapper.ProductRow("FI-SW-01", "FISH", "Angelfish", "d", null)));

        List<Product> products = service.productsByCategory("FISH");

        assertThat(products).extracting(Product::productId).containsExactly("FI-SW-01");
        assertThat(products.get(0).imagePath()).isNull();
    }

    @Test
    void unknownCategoryYieldsEmptyList() {
        when(mapper.selectProductsByCategory("NOPE")).thenReturn(List.of());

        assertThat(service.productsByCategory("NOPE")).isEmpty();
    }

    @Test
    void missingProductAndItemAreEmpty() {
        when(mapper.selectProduct("NOPE")).thenReturn(null);
        when(mapper.selectItem("NOPE")).thenReturn(null);

        assertThat(service.product("NOPE")).isEmpty();
        assertThat(service.item("NOPE")).isEmpty();
    }

    @Test
    void productDetailCombinesProductAndItsItems() {
        when(mapper.selectProduct("FI-SW-01")).thenReturn(
                new CatalogMapper.ProductRow("FI-SW-01", "FISH", "Angelfish", "d", null));
        when(mapper.selectItemsByProduct("FI-SW-01")).thenReturn(List.of(
                new CatalogMapper.ItemRow("EST-1", "FI-SW-01", "Angelfish", "16.50", "Large", "Adult", null, null,
                        null, 10000, "P")));

        Optional<ProductDetail> detail = service.product("FI-SW-01");

        assertThat(detail).isPresent();
        assertThat(detail.get().items()).hasSize(1);
        assertThat(detail.get().items().get(0).availableQuantity()).isEqualTo(10000);
        assertThat(detail.get().items().get(0).unitPrice().toPlainString()).isEqualTo("16.50");
    }

    @Test
    void searchUsesTheProductNameQueryOnly() {
        when(mapper.selectProductsByName("angel")).thenReturn(List.of(
                new CatalogMapper.ProductRow("FI-SW-01", "FISH", "Angelfish", "d", null)));

        List<Product> matches = service.searchByName("angel");

        assertThat(matches).extracting(Product::productId).containsExactly("FI-SW-01");
        verify(mapper).selectProductsByName("angel");
        verify(mapper, org.mockito.Mockito.never()).selectProduct(anyString());
    }

    @Test
    void missingBannerIsEmptyRatherThanAFailure() {
        when(mapper.selectBanner("BIRDS")).thenReturn(null);

        assertThat(service.banner("BIRDS")).isEmpty();
    }

    @Test
    void bannerIsMappedToImageReferenceAndText() {
        when(mapper.selectBanner("FISH")).thenReturn(new CatalogMapper.BannerRow("FISH", "fish_banner"));

        Optional<Banner> banner = service.banner("FISH");

        assertThat(banner).isPresent();
        assertThat(banner.get().description()).isEqualTo("fish_banner");
        assertThat(banner.get().imagePath()).isNotBlank();
    }

    @Test
    void priceIsReadFromTheServerSideProjection() {
        when(mapper.selectItem("EST-1")).thenReturn(
                new CatalogMapper.ItemRow("EST-1", "FI-SW-01", "Angelfish", "16.50", "Large", "Adult", null, null,
                        null, 3, "P"));

        assertThat(service.priceOf("EST-1").toPlainString()).isEqualTo("16.50");
        assertThat(service.availableQuantity("EST-1")).isEqualTo(3);
    }

    @Test
    void priceOfAnUnknownItemFails() {
        when(mapper.selectItem("NOPE")).thenReturn(null);

        assertThatThrownBy(() -> service.priceOf("NOPE"))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.RESOURCE_NOT_FOUND);
    }

    @Test
    void purchasableReflectsPositiveStock() {
        when(mapper.selectItem("EST-1")).thenReturn(
                new CatalogMapper.ItemRow("EST-1", "FI-SW-01", "Angelfish", "16.50", "Large", "Adult", null, null,
                        null, 0, "P"));

        assertThat(service.isPurchasable("EST-1")).isFalse();
    }

    @Test
    void delistedItemsAreNeverPurchasable() {
        when(mapper.selectItem("EST-9")).thenReturn(
                new CatalogMapper.ItemRow("EST-9", "FI-SW-01", "Angelfish", "16.50", "Large", "Adult", null, null,
                        null, 100, "D"));

        assertThat(service.isPurchasable("EST-9"))
                .as("a delisted item must not be sold (Q10), even with stock on hand")
                .isFalse();
    }
}
