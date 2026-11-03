package ik.am.jpetstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T175: boot entry point and scanning boundary of the modern backend.
 *
 * <p>The application class anchors component scanning at {@code ik.am.jpetstore}, does not
 * import any legacy {@code spring-jpetstore-*.xml} resource, and its configuration can start
 * a base context with explicit configuration (infrastructure auto-configurations excluded,
 * because no database is available at this layer).</p>
 */
class ApplicationBoundaryTest {

    private static final Class<?> APPLICATION = JPetStoreApplication.class;

    @Test
    void bootEntryPointAnchorsTheModernRootPackage() {
        assertThat(APPLICATION.getPackageName())
                .as("component scanning must anchor at the modern root package")
                .isEqualTo("ik.am.jpetstore");
        assertThat(AnnotatedElementUtils.findMergedAnnotation(APPLICATION, SpringBootConfiguration.class))
                .as("@SpringBootApplication entry point is required")
                .isNotNull();
    }

    @Test
    void configurationDoesNotImportLegacySpringXmlOrWidenScanning() {
        assertThat(APPLICATION.getAnnotation(ImportResource.class))
                .as("legacy spring-jpetstore-*.xml must never be imported")
                .isNull();
        ComponentScan componentScan = AnnotatedElementUtils.findMergedAnnotation(APPLICATION, ComponentScan.class);
        assertThat(componentScan == null || componentScan.basePackages().length == 0)
                .as("explicit ComponentScan basePackages must not widen scanning beyond ik.am.jpetstore")
                .isTrue();
    }

    @Test
    void baseContextStartsFromTheApplicationConfiguration() {
        new WebApplicationContextRunner()
                .withUserConfiguration(APPLICATION)
                // Phase 7 起真实转换器已经存在：这里不再提供占位，完整上下文必须用真实实现装配成功。
                .withBean(ik.am.jpetstore.account.infrastructure.AccountMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.account.infrastructure.AccountMapper.class))
                .withBean(ik.am.jpetstore.catalog.infrastructure.CatalogMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.catalog.infrastructure.CatalogMapper.class))
                .withBean(ik.am.jpetstore.cart.infrastructure.CartMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.cart.infrastructure.CartMapper.class))
                .withBean(ik.am.jpetstore.order.infrastructure.CheckoutMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.order.infrastructure.CheckoutMapper.class))
                .withBean(ik.am.jpetstore.order.infrastructure.InventoryMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.order.infrastructure.InventoryMapper.class))
                .withBean(ik.am.jpetstore.order.infrastructure.OrderMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.order.infrastructure.OrderMapper.class))
                .withBean(ik.am.jpetstore.order.infrastructure.OrderRequestMapper.class,
                        () -> org.mockito.Mockito.mock(ik.am.jpetstore.order.infrastructure.OrderRequestMapper.class))
                .withPropertyValues(
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
                .run(context -> {
                    assertThat(context).as("base context starts with explicit configuration").hasNotFailed();
                    assertThat(context).hasSingleBean(JPetStoreApplication.class);
                    assertThat(context.getBean(JPetStoreApplication.class))
                            .as("the application configuration itself is a usable bean")
                            .isInstanceOf(JPetStoreApplication.class);
                });
    }
}
