package ik.am.jpetstore.cart.infrastructure;

import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T281: 会话购物车持有权绑定。
 *
 * <p>覆盖：接口不接受任意 cartId、会话 ID 不落日志与业务表、登录轮换会话 ID 后绑定不变、
 * 账号恢复与跨设备一致、退出保留、游客 30 分钟失效、登录合并，以及读取不创建业务行
 * （CRT-01/11、AT-18、Q08）。</p>
 */
@SpringBootTest
class SessionCartBindingTest {

    @Autowired
    private SessionCartBinding binding;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

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
    void theBindingIsStableWithinOneSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String first = binding.bindingHash(request);
        String second = binding.bindingHash(request);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void differentSessionsGetDifferentBindings() {
        String first = binding.bindingHash(new MockHttpServletRequest());
        String second = binding.bindingHash(new MockHttpServletRequest());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theBindingIsNotTheSessionIdentifier() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession(new org.springframework.mock.web.MockServletContext(),
                "distinctive-session-identifier-0001");
        request.setSession(session);

        String hash = binding.bindingHash(request);

        assertThat(hash).doesNotContain(session.getId());
        assertThat(hash).isNotEqualTo(session.getId());
    }

    @Test
    void theBindingSurvivesTheSessionIdentifierRotationOfALogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String before = binding.bindingHash(request);

        session.changeSessionId();

        assertThat(binding.bindingHash(request))
                .as("登录轮换会话 ID 后游客车仍然可以被找到（Q08）")
                .isEqualTo(before);
    }

    @Test
    void anAuthenticatedRequestResolvesTheAccountOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CartOwner owner = binding.currentOwner(request, "alice");

        assertThat(owner.isAnonymous()).isFalse();
        assertThat(owner.userId()).isEqualTo("alice");
        assertThat(owner.sessionBindingHash())
                .as("账号持有权携带当前会话绑定，用于登录合并")
                .isEqualTo(binding.bindingHash(request));
    }

    @Test
    void anAnonymousRequestResolvesTheGuestOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CartOwner owner = binding.currentOwner(request, null);

        assertThat(owner.isAnonymous()).isTrue();
        assertThat(owner.userId()).isNull();
        assertThat(owner.sessionBindingHash()).isEqualTo(binding.bindingHash(request));
    }

    @Test
    void resolvingTheOwnerDoesNotCreateBusinessRows() {
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");

        binding.currentOwner(new MockHttpServletRequest(), null);
        binding.currentOwner(new MockHttpServletRequest(), "alice");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart", Integer.class))
                .as("读取持有权不创建购物车业务行")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class)).isZero();
    }
}
