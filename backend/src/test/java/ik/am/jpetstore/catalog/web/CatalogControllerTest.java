package ik.am.jpetstore.catalog.web;

import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T262: Catalog REST适配。
 *
 * <p>验证分类、产品、可售项与搜索路由，以及 404 与空词 400；响应是 JSON，不回退为 HTML。
 * 只 mock 应用端口与转换端口，不禁用安全过滤器（CAT-01～08、AT-05/06）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQuery catalogQuery;

    @MockitoBean
    private CatalogAssembler assembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @Test
    void listsCategories() throws Exception {
        given(catalogQuery.categories()).willReturn(List.of(new Category("FISH", "Fish", "d")));
        given(assembler.toCategory(org.mockito.ArgumentMatchers.any())).willReturn(
                new CatalogDtos.CategoryDto("FISH", "Fish", "d"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].categoryId").value("FISH"));
    }

    @Test
    void listsProductsOfACategory() throws Exception {
        given(catalogQuery.productsByCategory("FISH")).willReturn(List.of(new Product("FI-SW-01", "FISH", "Angelfish",
                "d", null)));
        given(assembler.toProduct(org.mockito.ArgumentMatchers.any())).willReturn(
                new CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish", "d", null));

        mockMvc.perform(get("/api/categories/FISH/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value("FI-SW-01"));
    }

    @Test
    void returnsProductDetailWithItems() throws Exception {
        given(catalogQuery.product("FI-SW-01")).willReturn(Optional.of(new ProductDetail(
                new Product("FI-SW-01", "FISH", "Angelfish", "d", null),
                List.of(new Item("EST-1", "FI-SW-01", "Angelfish", List.of("Large"), Money.cny("16.50"), 10, true,
                        true)))));
        given(assembler.toProductDetail(org.mockito.ArgumentMatchers.any())).willReturn(
                new CatalogDtos.ProductDetailDto(
                        new CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish", "d", null),
                        List.of(new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish", List.of("Large"), "16.50",
                                true, 10, true))));

        mockMvc.perform(get("/api/products/FI-SW-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.productId").value("FI-SW-01"))
                .andExpect(jsonPath("$.items[0].unitPrice").value("16.50"));
    }

    @Test
    void returnsASingleItem() throws Exception {
        given(catalogQuery.item("EST-1")).willReturn(Optional.of(new Item("EST-1", "FI-SW-01", "Angelfish",
                List.of("Large"), Money.cny(new BigDecimal("16.50")), 10, true, true)));
        given(assembler.toItem(org.mockito.ArgumentMatchers.any())).willReturn(
                new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish", List.of("Large"), "16.50", true, 10, true));

        mockMvc.perform(get("/api/items/EST-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value("EST-1"))
                .andExpect(jsonPath("$.unitPrice").value("16.50"));
    }

    @Test
    void searchesByProductName() throws Exception {
        given(catalogQuery.searchByName("angel")).willReturn(List.of(new Product("FI-SW-01", "FISH", "Angelfish",
                "d", null)));
        given(assembler.toProduct(org.mockito.ArgumentMatchers.any())).willReturn(
                new CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish", "d", null));

        mockMvc.perform(get("/api/products").param("keyword", "angel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value("FI-SW-01"));
    }

    @Test
    void emptySearchKeywordIsRejected() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void missingResourcesReturn404ProblemDetails() throws Exception {
        given(catalogQuery.product("NOPE")).willReturn(Optional.empty());
        given(catalogQuery.item("NOPE")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/products/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/items/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
