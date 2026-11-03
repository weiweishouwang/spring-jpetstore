package ik.am.jpetstore.baseline;

import ik.am.jpetstore.domain.model.Account;
import ik.am.jpetstore.domain.model.Order;
import org.junit.Test;
import org.springframework.dao.DataAccessException;
import static org.junit.Assert.*;

/** Real transaction proxies and H2: these tests do not mock persistence. */
public class LegacyServiceRegressionTest extends LegacyTestSupport {
    @Test public void duplicateUsernameDoesNotOverwriteExistingAccount() {
        String before = accounts.getAccount("j2ee").getEmail();
        try {
            accounts.insertAccount(newAccount("j2ee"));
            fail("A duplicate username must be rejected");
        } catch (DataAccessException expected) {
            assertEquals(before, accounts.getAccount("j2ee").getEmail());
            assertEquals(2, jdbc.queryForInt("select count(*) from account"));
        }
    }

    @Test public void registrationRollsBackWhenCredentialsCannotBeWritten() {
        Account invalid = newAccount("rollback-user");
        invalid.setPassword(null); // account + profile insert first; signon then fails.
        try {
            accounts.insertAccount(invalid);
            fail("Null credentials must fail the transaction");
        } catch (DataAccessException expected) {
            assertEquals(0, jdbc.queryForInt("select count(*) from account where userid='rollback-user'"));
            assertEquals(0, jdbc.queryForInt("select count(*) from profile where userid='rollback-user'"));
            assertEquals(0, jdbc.queryForInt("select count(*) from signon where username='rollback-user'"));
        }
    }

    @Test public void emptyPasswordPreservesCredentialsAndNonemptyPasswordChangesThem() throws Exception {
        Account account = accounts.getAccount("j2ee");
        account.setFirstName("Changed");
        account.setPassword("");
        accounts.updateAccount(account);
        assertEquals("Changed", accounts.getAccount("j2ee").getFirstName());
        login("j2ee", "j2ee");
        account.setPassword("new-password");
        accounts.updateAccount(account);
        login("j2ee", "new-password");
        assertNull(accounts.getAccount("j2ee", "j2ee"));
    }

    // AT-12: fail the second line insert, after stock/header/status/first line.
    @Test public void orderFailureRollsBackInventoryHeaderStatusLinesAndSequence() {
        Order order = order("j2ee", "EST-1", 2);
        order.addLineItem(order.getLineItems().get(0)); // duplicate (orderid, linenum)
        try {
            orders.insertOrder(order);
            fail("Duplicate detail keys must fail the transaction");
        } catch (DataAccessException expected) {
            assertEquals(10000, jdbc.queryForInt("select qty from inventory where itemid='EST-1'"));
            assertEquals(0, jdbc.queryForInt("select count(*) from orders"));
            assertEquals(0, jdbc.queryForInt("select count(*) from lineitem"));
            assertEquals(0, jdbc.queryForInt("select count(*) from orderstatus"));
            assertEquals(1000, jdbc.queryForInt("select nextid from sequence where name='ordernum'"));
        }
    }
}
