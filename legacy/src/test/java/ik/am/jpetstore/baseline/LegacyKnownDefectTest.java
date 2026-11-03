package ik.am.jpetstore.baseline;

import java.math.BigDecimal;
import java.util.Collections;
import ik.am.jpetstore.app.cart.CartForm;
import ik.am.jpetstore.app.cart.CartHelper;
import ik.am.jpetstore.domain.model.Cart;
import ik.am.jpetstore.domain.model.Order;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Characterization of defects, NOT future acceptance requirements.
 * When fixing an issue, replace its assertion with the desired invariant.
 * See docs/baseline/decisions.md for issue IDs and replacement criteria.
 */
public class LegacyKnownDefectTest extends LegacyTestSupport {
    @Test public void d04QuantityZeroLeavesAnInvisibleCartIndexEntry() {
        Cart cart = cart("EST-1", 1);
        CartForm form = new CartForm();
        form.setQuantity(Collections.singletonMap("EST-1", Integer.valueOf(0)));
        CartHelper helper = context.getBean(CartHelper.class);
        helper.updateCartQuantities(form, cart);
        assertEquals(0, cart.getNumberOfItems());
        assertTrue("Known defect: list and index disagree", cart.containsItemId("EST-1"));
        helper.addItemToCart("EST-1", cart);
        assertEquals("Known defect: re-added SKU remains invisible", 0, cart.getNumberOfItems());
    }

    @Test public void d02OrdersCanDeductMoreThanAvailableStock() {
        orders.insertOrder(order("j2ee", "EST-1", 10001));
        assertEquals("Known defect, not a supported backorder requirement", -1,
            jdbc.queryForInt("select qty from inventory where itemid='EST-1'"));
    }

    @Test public void d05HistoricalLineTotalUsesCurrentPriceInsteadOfPaidUnitPrice() {
        Order order = order("j2ee", "EST-1", 2);
        orders.insertOrder(order);
        Order historical = orders.getOrder(order.getOrderId());
        historical.getLineItems().get(0).getItem().setListPrice(new BigDecimal("20.00"));
        // Re-associate exactly as OrderService.getOrder does when loading current item data.
        historical.getLineItems().get(0).setItem(historical.getLineItems().get(0).getItem());
        assertEquals(new BigDecimal("16.50"), historical.getLineItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("40.00"), historical.getLineItems().get(0).getTotal());
        assertEquals(new BigDecimal("33.00"), historical.getTotalPrice());
    }

    @Test public void d06ServiceAcceptsAnEmptyOrder() {
        Order empty = new Order();
        empty.initOrder(accounts.getAccount("j2ee"), new Cart());
        orders.insertOrder(empty);
        assertEquals(1, jdbc.queryForInt("select count(*) from orders"));
        assertEquals(0, jdbc.queryForInt("select count(*) from lineitem"));
    }
}
