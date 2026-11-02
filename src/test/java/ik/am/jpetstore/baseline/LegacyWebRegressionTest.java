package ik.am.jpetstore.baseline;

import java.math.BigDecimal;
import ik.am.jpetstore.domain.model.Cart;
import ik.am.jpetstore.domain.model.Order;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Preserved behaviour, exercised through MVC and the real security filter. */
public class LegacyWebRegressionTest extends LegacyTestSupport {
    // AT-01: use all mandatory fields, including legacy-required address2.
    @Test public void registrationCreatesAccountAndRequiresExplicitLogin() throws Exception {
        MvcResult result = mvc.perform(post("/account/newAccount")
            .param("username", "baseline").param("password", "pass123")
            .param("repeatedPassword", "pass123").param("email", "base@example.com")
            .param("firstName", "Base").param("lastName", "Line").param("phone", "555")
            .param("address1", "1 Road").param("address2", "Room 2").param("city", "City")
            .param("state", "CA").param("zip", "12345").param("country", "USA")
            .param("languagePreference", "english").param("favouriteCategoryId", "FISH"))
            .andExpect(status().isFound()).andReturn();
        assertEquals("/account/signonForm", new java.net.URI(result.getResponse().getRedirectedUrl()).getPath());
        assertNotNull(accounts.getAccount("baseline"));
        assertEquals(1, jdbc.queryForInt("select count(*) from profile where userid='baseline'"));
        assertEquals(1, jdbc.queryForInt("select count(*) from signon where username='baseline'"));
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(session == null || session.getAttribute("SPRING_SECURITY_CONTEXT") == null);
        login("baseline", "pass123");
    }

    // AT-02: mismatch cannot write any of the three registration records.
    @Test public void mismatchedPasswordsDoNotRegister() throws Exception {
        mvc.perform(post("/account/newAccount").param("username", "rejected")
            .param("password", "one").param("repeatedPassword", "two"))
            .andExpect(view().name("account/NewAccountForm"))
            .andExpect(model().attributeHasFieldErrors("accountForm", "password"));
        assertEquals(0, jdbc.queryForInt("select count(*) from signon where username='rejected'"));
        assertEquals(0, jdbc.queryForInt("select count(*) from account where userid='rejected'"));
        assertEquals(0, jdbc.queryForInt("select count(*) from profile where userid='rejected'"));
    }

    @Test public void wrongPasswordDoesNotAuthenticate() throws Exception {
        MvcResult result = mvc.perform(post("/account/signon")
            .param("j_username", "j2ee").param("j_password", "wrong"))
            .andExpect(redirectedUrl("/account/signonForm?error=true")).andReturn();
        assertNull(result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT"));
    }

    @Test public void loginProtectsOrdersAndAccountEditing() throws Exception {
        String[] paths = {"/order/newOrderForm", "/order/listOrders", "/order/viewOrder?orderId=1",
            "/account/editAccountForm"};
        for (String path : paths) {
            MvcResult result = mvc.perform(get(path)).andExpect(status().isFound()).andReturn();
            assertTrue(result.getResponse().getRedirectedUrl().endsWith("/account/signonForm"));
        }
    }

    // AT-05/06: SQL-backed browsing and case-insensitive name search.
    @Test public void guestsBrowseCatalogAndSearchByProductName() throws Exception {
        mvc.perform(get("/catalog/viewCategory").param("categoryId", "FISH"))
            .andExpect(view().name("catalog/Category")).andExpect(model().attributeExists("category", "productList"));
        mvc.perform(get("/catalog/viewProduct").param("productId", "FI-SW-01"))
            .andExpect(view().name("catalog/Product")).andExpect(model().attributeExists("product", "itemList"));
        mvc.perform(get("/catalog/viewItem").param("itemId", "EST-1"))
            .andExpect(view().name("catalog/Item"));
        MvcResult result = mvc.perform(get("/catalog").param("keyword", "ANGEL"))
            .andExpect(view().name("catalog/SearchProducts")).andReturn();
        assertEquals(1, ((java.util.List<?>) result.getModelAndView().getModel().get("productList")).size());
        assertTrue(catalog.searchProductList("Australia").isEmpty());
        mvc.perform(get("/catalog").param("keyword", "" )).andExpect(view().name("common/Error"));
    }

    // AT-07/08: same SKU merges; positive quantity update and explicit removal.
    @Test public void guestCartMergesQuantitiesAndCalculatesTotals() throws Exception {
        MockHttpSession session = new MockHttpSession();
        add(session, "EST-1");
        add(session, "EST-1");
        add(session, "EST-2");
        Cart cart = sessionCart(session);
        assertEquals(2, cart.getNumberOfItems());
        assertEquals(new BigDecimal("49.50"), cart.getSubTotal());
        mvc.perform(post("/cart/updateCartQuantities").session(session)
            .param("quantity[EST-1]", "3").param("quantity[EST-2]", "1"))
            .andExpect(redirectedUrl("/cart/viewCart"));
        assertEquals(new BigDecimal("66.00"), cart.getSubTotal());
        mvc.perform(get("/cart/removeItemFromCart").session(session).param("cartItem", "EST-2"));
        assertEquals(1, cart.getNumberOfItems());
        assertEquals(new BigDecimal("49.50"), cart.getSubTotal());
        assertEquals(10000, jdbc.queryForInt("select qty from inventory where itemid='EST-1'"));
    }

    @Test public void separateSessionsDoNotShareCarts() throws Exception {
        MockHttpSession first = new MockHttpSession();
        MockHttpSession second = new MockHttpSession();
        add(first, "EST-1");
        assertEquals(1, sessionCart(first).getNumberOfItems());
        assertEquals(0, sessionCart(second).getNumberOfItems());
    }

    // AT-10/11: the real multistep controller and helper persist an owned order.
    @Test public void checkoutPersistsAddressesLinesAndInventoryWithoutChangingAccount() throws Exception {
        MockHttpSession session = login("j2ee", "j2ee");
        add(session, "EST-1");
        add(session, "EST-1");
        mvc.perform(get("/order/newOrderForm").session(session))
            .andExpect(view().name("order/NewOrderForm"));
        mvc.perform(post("/order/newOrder").session(session).param("shippingAddressRequired", "true"))
            .andExpect(view().name("order/ShippingForm"));
        mvc.perform(post("/order/newOrder").session(session))
            .andExpect(view().name("order/ConfirmOrder"));
        String oldAddress = accounts.getAccount("j2ee").getAddress1();
        mvc.perform(post("/order/newOrder").session(session).param("confirmed", "true")
            .param("cardType", "Visa").param("creditCard", "FAKE-CARD").param("expiryDate", "12/2030")
            .param("billToFirstName", "Bill").param("billToLastName", "Buyer")
            .param("billAddress1", "Billing Road").param("billCity", "City").param("billState", "CA")
            .param("billZip", "12345").param("billCountry", "USA")
            .param("shipToFirstName", "Ship").param("shipToLastName", "Buyer")
            .param("shipAddress1", "Shipping Road").param("shipCity", "City").param("shipState", "CA")
            .param("shipZip", "12345").param("shipCountry", "USA"))
            .andExpect(status().isFound());
        Order order = orders.getOrdersByUsername("j2ee").get(0);
        Order saved = orders.getOrder(order.getOrderId());
        assertEquals("Shipping Road", saved.getShipAddress1());
        assertEquals("Billing Road", saved.getBillAddress1());
        assertEquals(oldAddress, accounts.getAccount("j2ee").getAddress1());
        assertEquals(new BigDecimal("33.00"), saved.getTotalPrice());
        assertEquals(1, saved.getLineItems().size());
        assertEquals(2, saved.getLineItems().get(0).getQuantity());
        assertEquals("P", saved.getStatus());
        assertEquals(9998, jdbc.queryForInt("select qty from inventory where itemid='EST-1'"));
        assertEquals(1, jdbc.queryForInt("select count(*) from orderstatus"));
    }

    // AT-13: list and detail both enforce ownership at the web boundary.
    @Test public void usersCanOnlyReadTheirOwnOrders() throws Exception {
        Order mine = order("j2ee", "EST-1", 1);
        Order theirs = order("ACID", "EST-2", 1);
        orders.insertOrder(mine);
        orders.insertOrder(theirs);
        MockHttpSession session = login("j2ee", "j2ee");
        MvcResult list = mvc.perform(get("/order/listOrders").session(session)).andReturn();
        java.util.List<?> visible = (java.util.List<?>) list.getModelAndView().getModel().get("orderList");
        assertEquals(1, visible.size());
        assertEquals(mine.getOrderId(), ((Order) visible.get(0)).getOrderId());
        mvc.perform(get("/order/viewOrder").session(session).param("orderId", String.valueOf(mine.getOrderId())))
            .andExpect(view().name("order/ViewOrder")).andExpect(model().attributeExists("order"));
        MvcResult denied = mvc.perform(get("/order/viewOrder").session(session)
            .param("orderId", String.valueOf(theirs.getOrderId())))
            .andExpect(view().name("common/Error")).andReturn();
        assertFalse(denied.getModelAndView().getModel().containsKey("order"));
    }

    private void add(MockHttpSession session, String itemId) throws Exception {
        mvc.perform(get("/cart/addItemToCart").session(session).param("workingItemId", itemId))
            .andExpect(redirectedUrl("/cart/viewCart"));
    }

    private Cart sessionCart(MockHttpSession session) throws Exception {
        mvc.perform(get("/cart/viewCart").session(session));
        // The model contains a scoped proxy. Inspect its stored target rather
        // than dereferencing the proxy outside the MVC request's session.
        Cart target = (Cart) session.getAttribute("scopedTarget.cart");
        if (target == null) return new Cart(); // An untouched session has no target yet.
        return target;
    }
}
