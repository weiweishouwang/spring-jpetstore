package ik.am.jpetstore.architecture;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T230: 模块依赖与分层架构守卫。
 *
 * <p>规则对以后新增的类持续生效（plan §3.2）：业务模块之间不得成环；领域模型不得依赖 Web
 * 或持久层；跨模块只能通过公开 api 包交互，不能直接访问其他模块的 infrastructure。
 * catalog 的只读展示投影是明确允许的跨模块只读访问。</p>
 */
class ModuleBoundariesTest {

    private static final String BASE = "ik.am.jpetstore.";

    private static final List<String> MODULES = List.of("account", "identity", "catalog", "cart", "order", "shared");

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("ik.am.jpetstore");
    }

    @Test
    void everyModuleHasClassesSoTheRulesAreNotVacuous() {
        List<String> packages = importedClasses.stream()
                .map(type -> type.getPackageName())
                .filter(name -> name.startsWith(BASE))
                .map(name -> name.substring(BASE.length()).split("\\.")[0])
                .distinct()
                .toList();

        assertThat(packages).as("no rule may pass by skipping an empty module").containsAll(MODULES);
    }

    @Test
    void businessModulesDoNotDependOnEachOtherCyclically() {
        slices().matching(BASE + "(*)..").should().beFreeOfCycles();
    }

    @Test
    void domainTypesDoNotDependOnWebOrPersistence() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.stereotype..",
                        "jakarta.servlet..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..");
    }

    @Test
    void cartDoesNotReachIntoOrderOrAccountInternals() {
        noClasses().that().resideInAPackage(BASE + "cart..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "order..",
                        BASE + "account..",
                        BASE + "identity..");
    }

    @Test
    void accountAndCatalogDoNotReachIntoOrderOrCartInternals() {
        noClasses().that().resideInAPackage(BASE + "account..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + "order..", BASE + "cart..");
        noClasses().that().resideInAPackage(BASE + "catalog..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + "order..", BASE + "cart..");
    }

    @Test
    void publicApiPackagesDoNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAPackage(BASE + "shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "account..",
                        BASE + "identity..",
                        BASE + "catalog..",
                        BASE + "cart..",
                        BASE + "order..");
    }
}
