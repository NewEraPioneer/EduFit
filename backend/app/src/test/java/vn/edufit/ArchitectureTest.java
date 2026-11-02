package vn.edufit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Kiểm thử kiến trúc tự động bằng ArchUnit (ADR-002).
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Cưỡng chế các ranh giới package kiến trúc ở mức kiểm thử đơn vị tự động.</li>
 *   <li>Ngăn ngừa việc các module gọi trực tiếp vào tầng nội bộ (domain/application/infra) của module khác.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "vn.edufit", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {
  private static final Set<String> MODULES = Set.of("iam", "profile", "verification", "discovery",
      "connection", "scheduling", "progress", "review", "notification", "payment");
  private static final String[] MODULE_PACKAGES = MODULES.stream()
      .map(module -> "vn.edufit." + module + "..").toArray(String[]::new);

  @ArchTest
  static final ArchRule modules_should_only_interact_through_api = classes()
      .that().resideInAnyPackage(MODULE_PACKAGES)
      .should(new ArchCondition<JavaClass>("access other business modules only through their api packages") {
        @Override public void check(JavaClass source, ConditionEvents events) {
          String owner = moduleOf(source);
          for (var dependency : source.getDirectDependenciesFromSelf()) {
            String target = moduleOf(dependency.getTargetClass());
            String api = "vn.edufit." + target + ".api";
            String targetPackage = dependency.getTargetClass().getPackageName();
            if (target != null && !target.equals(owner)
                && !targetPackage.equals(api) && !targetPackage.startsWith(api + ".")) {
              events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
            }
          }
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule no_module_should_depend_on_notification = noClasses()
      .that().resideInAnyPackage(MODULES.stream().filter(module -> !module.equals("notification"))
          .map(module -> "vn.edufit." + module + "..").toArray(String[]::new))
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.notification..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule root_packages_should_have_no_dependency_cycles = slices()
      .matching("vn.edufit.(*)..").should().beFreeOfCycles().allowEmptyShould(false);

  private static String moduleOf(JavaClass type) {
    String packageName = type.getPackageName();
    for (String module : MODULES) {
      String root = "vn.edufit." + module;
      if (packageName.equals(root) || packageName.startsWith(root + ".")) return module;
    }
    return null;
  }
}
