package in.rsh.cab.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(packages = "in.rsh.cab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..internal.persistence..", "in.rsh.cab.repository..");

  @ArchTest
  static final ArchRule MODULE_WEB_ADAPTERS_ARE_NOT_SHARED =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("..internal.web..")
          .should(notDependOnAnotherModulesWebAdapter());

  private static ArchCondition<JavaClass> notDependOnAnotherModulesWebAdapter() {
    return new ArchCondition<>("not depend on another module's web adapter") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        String sourceModule = module(source);
        source.getDirectDependenciesFromSelf().stream()
            .map(dependency -> dependency.getTargetClass())
            .filter(target -> target.getPackageName().contains(".internal.web"))
            .filter(target -> !module(target).equals(sourceModule))
            .forEach(
                target ->
                    events.add(
                        SimpleConditionEvent.violated(
                            source, source.getName() + " depends on " + target.getName())));
      }
    };
  }

  private static String module(JavaClass type) {
    String[] segments = type.getPackageName().split("\\.");
    return segments.length > 3 ? segments[3] : type.getPackageName();
  }
}
