package ik.am.jpetstore.baseline;

import javax.servlet.Filter;
import javax.sql.DataSource;

import ik.am.jpetstore.domain.model.Account;
import ik.am.jpetstore.domain.model.Cart;
import ik.am.jpetstore.domain.model.Order;
import ik.am.jpetstore.domain.service.account.AccountService;
import ik.am.jpetstore.domain.service.catalog.CatalogService;
import ik.am.jpetstore.domain.service.order.OrderService;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Uses the original contexts, security filter, services, mappers and H2 scripts. */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration("src/main/webapp")
@ContextConfiguration(locations = {
    "classpath:META-INF/spring/applicationContext.xml",
    "classpath:META-INF/spring/spring-security.xml",
    "classpath:META-INF/spring/spring-mvc.xml"
})
public abstract class LegacyTestSupport {
    @Autowired protected WebApplicationContext context;
    @Autowired protected DataSource dataSource;
    @Autowired protected AccountService accounts;
    @Autowired protected CatalogService catalog;
    @Autowired protected OrderService orders;
    @Autowired private SqlSessionFactory sqlSessions;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter security;
    protected JdbcTemplate jdbc;
    protected MockMvc mvc;

    @Before
    public void resetDatabaseAndSessions() throws Exception {
        SecurityContextHolder.clearContext();
        java.sql.Connection connection = dataSource.getConnection();
        try {
            assertTrue("Baseline may reset only an H2 in-memory database",
                connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        } finally {
            connection.close();
        }
        jdbc = new JdbcTemplate(dataSource);
        // The legacy pool defaults autoCommit=false: use Spring's script executor
        // so fixture writes are committed, rather than relying on pool behaviour.
        ResourceDatabasePopulator fixtures = new ResourceDatabasePopulator();
        fixtures.addScript(new ClassPathResource("baseline-reset.sql"));
        fixtures.addScript(new ClassPathResource("database/H2-schema.sql"));
        fixtures.addScript(new ClassPathResource("database/H2-dataload.sql"));
        DatabasePopulatorUtils.execute(fixtures, dataSource);
        for (String name : sqlSessions.getConfiguration().getCacheNames()) {
            if (name.indexOf('.') >= 0) sqlSessions.getConfiguration().getCache(name).clear();
        }
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    protected MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/account/signon")
            .param("j_username", username).param("j_password", password)).andReturn();
        assertEquals(302, result.getResponse().getStatus());
        assertFalse(String.valueOf(result.getResponse().getRedirectedUrl()).contains("error=true"));
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        return session;
    }

    protected Cart cart(String itemId, int quantity) {
        Cart cart = new Cart();
        cart.addItem(catalog.getItem(itemId), catalog.isItemInStock(itemId));
        cart.setQuantityByItemId(itemId, quantity);
        return cart;
    }

    protected Order order(String username, String itemId, int quantity) {
        Order order = new Order();
        order.initOrder(accounts.getAccount(username), cart(itemId, quantity));
        return order;
    }

    protected Account newAccount(String username) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("baseline-pass");
        account.setEmail("baseline@example.com");
        account.setFirstName("Base");
        account.setLastName("Line");
        account.setPhone("555-0100");
        account.setAddress1("1 Test Road");
        account.setAddress2("Room 2");
        account.setCity("Test City");
        account.setState("CA");
        account.setZip("12345");
        account.setCountry("USA");
        account.setLanguagePreference("english");
        account.setFavouriteCategoryId("FISH");
        return account;
    }
}
