package ik.am.jpetstore.account.web;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountQuery;
import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;
import ik.am.jpetstore.account.domain.AccountModels.AccountProfile;
import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Credentials;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;
import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T264: Account REST适配。
 *
 * <p>注册返回 201 且不建立登录身份；me 与 PATCH 只作用于当前身份；响应不含密码或哈希；推荐与
 * 横幅按本人偏好与开关返回（ACC-01～09、PER-01～04、AT-01～04/14/16）。只 mock 应用端口与
 * 转换端口，不禁用安全过滤器。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRegistration registration;

    @MockitoBean
    private AccountProfileUpdate profileUpdate;

    @MockitoBean
    private AccountQuery accountQuery;

    @MockitoBean
    private AccountAssembler accountAssembler;

    @MockitoBean
    private CatalogQuery catalogQuery;

    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @Test
    void registrationReturns201AndDoesNotLogTheUserIn() throws Exception {
        given(accountAssembler.toResponse(any())).willReturn(new AccountDtos.AccountResponse("newbie",
                "newbie@example.com", "070-1111-2222", dtoAddress(), dtoPreferences()));

        mockMvc.perform(post("/api/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newbie"));

        mockMvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownProfileIsReturnedForTheAuthenticatedUser() throws Exception {
        given(accountQuery.findByUserId("alice")).willReturn(Optional.of(details()));
        given(accountAssembler.toResponse(any())).willReturn(new AccountDtos.AccountResponse("alice",
                "alice@example.com", "070-1111-2222", dtoAddress(), dtoPreferences()));

        mockMvc.perform(get("/api/account/me").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.preferences.favouriteCategoryId").value("FISH"));
    }

    @Test
    void profileUpdateOnlyTouchesTheAuthenticatedAccount() throws Exception {
        given(accountQuery.findByUserId("alice")).willReturn(Optional.of(details()));
        given(accountAssembler.toResponse(any())).willReturn(new AccountDtos.AccountResponse("alice",
                "alice-new@example.com", "070-1111-2222", dtoAddress(), dtoPreferences()));

        mockMvc.perform(patch("/api/account/me").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice-new@example.com"));

        verify(profileUpdate).update(eq("alice"), any());
    }

    @Test
    void responsesNeverContainCredentials() throws Exception {
        given(accountQuery.findByUserId("alice")).willReturn(Optional.of(details()));
        given(accountAssembler.toResponse(any())).willReturn(new AccountDtos.AccountResponse("alice",
                "alice@example.com", "070-1111-2222", dtoAddress(), dtoPreferences()));

        String body = mockMvc.perform(get("/api/account/me").with(user("alice")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$");
    }

    @Test
    void recommendationsUseTheFavouriteCategoryOfTheCurrentUser() throws Exception {
        given(accountQuery.findByUserId("alice")).willReturn(Optional.of(details()));
        given(catalogQuery.productsOfCategory("FISH")).willReturn(List.of(
                new Product("FI-SW-01", "FISH", "Angelfish", "d", null)));
        given(catalogAssembler.toProduct(any())).willReturn(
                new ik.am.jpetstore.catalog.web.CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish", "d", null));

        mockMvc.perform(get("/api/account/me/recommendations").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value("FI-SW-01"));
    }

    @Test
    void missingBannerIsReportedAsNull() throws Exception {
        given(accountQuery.findByUserId("alice")).willReturn(Optional.of(details()));
        given(catalogQuery.banner("FISH")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/account/me/banner").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    private AccountDetails details() {
        return new AccountDetails(
                new AccountProfile("alice", "alice@example.com", "Alice", "Ishikawa", "070-1111-2222",
                        domainAddress()),
                domainPreferences(),
                new Credentials("alice", "$2a$10$hash"));
    }

    private AccountDtos.Address dtoAddress() {
        return new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo",
                "150-0001", "JP");
    }

    private Address domainAddress() {
        return new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo", "150-0001", "JP");
    }

    private AccountDtos.Preferences dtoPreferences() {
        return new AccountDtos.Preferences("english", "FISH", true, true);
    }

    private Preferences domainPreferences() {
        return new Preferences("english", "FISH", true, true);
    }

    private String registerBody() {
        return """
                {
                  "username": "newbie",
                  "password": "alice-pass-123",
                  "repeatedPassword": "alice-pass-123",
                  "email": "newbie@example.com",
                  "phone": "070-1111-2222",
                  "address": {
                    "firstName": "Alice", "lastName": "Ishikawa", "address1": "1-2-3 Sakuragaoka",
                    "address2": null, "city": "Shibuya", "state": "Tokyo", "zip": "150-0001", "country": "JP"
                  },
                  "preferences": {
                    "languagePreference": "english", "favouriteCategoryId": "FISH",
                    "listOption": true, "bannerOption": false
                  }
                }
                """;
    }

    private String updateBody() {
        return """
                {
                  "email": "alice-new@example.com",
                  "phone": "070-1111-2222",
                  "address": {
                    "firstName": "Alice", "lastName": "Ishikawa", "address1": "5-6-7 Jingumae",
                    "address2": null, "city": "Shibuya", "state": "Tokyo", "zip": "150-0001", "country": "JP"
                  },
                  "preferences": {
                    "languagePreference": "english", "favouriteCategoryId": "FISH",
                    "listOption": true, "bannerOption": true
                  }
                }
                """;
    }
}
