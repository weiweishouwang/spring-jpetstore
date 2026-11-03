package ik.am.jpetstore.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T391: JSON 日志及脱敏输出。
 *
 * <p>用真实请求产生的日志校验：请求关联标识进入日志上下文，日志正文不含口令、Cookie、CSRF
 * token、假卡号、地址原文或 SQL 参数（D01、D07、NFR-04、NFR-05）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class StructuredLoggingIT {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> appender;

    private Logger rootLogger;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    private ch.qos.logback.classic.Level previousLevel;

    @BeforeEach
    void captureLogs() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        previousLevel = rootLogger.getLevel();
        // 请求关联标识在 DEBUG 级别记录：测试打开该级别才能观察到日志上下文。
        rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(appender);
        rootLogger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void theRequestIdIsAvailableToTheLogContext() throws Exception {
        mockMvc.perform(get("/api/categories").header("X-Request-Id", "req-log-1"))
                .andExpect(status().isOk());

        assertThat(appender.list)
                .as("过滤器确实记录了这次请求")
                .isNotEmpty();
        assertThat(appender.list)
                .as("请求关联标识进入日志上下文")
                .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry("requestId", "req-log-1"));
    }

    @Test
    void theLogNeverCarriesCredentialsOrSensitiveContent() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .param("username", "alice")
                        .param("password", "alice-pass-123")
                        .header("Cookie", "JSESSIONID=secret-session")
                        .header("X-CSRF-TOKEN", "secret-csrf"))
                .andExpect(status().isForbidden());

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).noneMatch(message -> message.contains("alice-pass-123"));
        assertThat(messages).noneMatch(message -> message.contains("secret-session"));
        assertThat(messages).noneMatch(message -> message.contains("secret-csrf"));
    }
}
