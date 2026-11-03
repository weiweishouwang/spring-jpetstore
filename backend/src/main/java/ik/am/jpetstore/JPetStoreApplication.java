package ik.am.jpetstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modern JPetStore backend entry point.
 *
 * <p>Component scanning anchors at {@code ik.am.jpetstore}; the business modules
 * (account, identity, catalog, cart, order, shared) live below this package. The legacy
 * application is archived under {@code legacy/} and is never part of this classpath.</p>
 */
@SpringBootApplication
public class JPetStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(JPetStoreApplication.class, args);
    }
}
