package in.rsh.cab.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "in.rsh.cab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String[] RETIRED_PACKAGES = {
    "in.rsh.cab.adapter..",
    "in.rsh.cab.chain..",
    "in.rsh.cab.constants..",
    "in.rsh.cab.controller..",
    "in.rsh.cab.entity..",
    "in.rsh.cab.mapper..",
    "in.rsh.cab.model..",
    "in.rsh.cab.repository..",
    "in.rsh.cab.service..",
    "in.rsh.cab.state..",
    "in.rsh.cab.strategy..",
    "in.rsh.cab.template..",
    "in.rsh.cab.util.."
  };

  @ArchTest
  static final ArchRule RETIRED_PROTOTYPE_PACKAGES_ARE_ABSENT =
      noClasses().should().resideInAnyPackage(RETIRED_PACKAGES);

  @ArchTest
  static final ArchRule RETIRED_PROTOTYPE_CONTROLLERS_ARE_ABSENT =
      noClasses()
          .should()
          .haveSimpleName("CityController")
          .orShould()
          .haveSimpleName("CabController")
          .orShould()
          .haveSimpleName("BookingController");

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

  @Test
  void applicationControllerPathsAreVersioned() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    var controllers = scanner.findCandidateComponents("in.rsh.cab");
    assertFalse(controllers.isEmpty());
    for (var controllerDefinition : controllers) {
      Class<?> controller = Class.forName(controllerDefinition.getBeanClassName());
      String[] basePaths = paths(controller);
      for (Method method : controller.getDeclaredMethods()) {
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
          continue;
        }
        for (String basePath : basePaths) {
          for (String methodPath :
              mapping.path().length == 0 ? new String[] {""} : mapping.path()) {
            String path = basePath + methodPath;
            assertTrue(
                path.startsWith("/api/v1/")
                    || path.startsWith("/actuator/")
                    || path.startsWith("/v3/api-docs")
                    || path.startsWith("/swagger-ui"),
                () -> controller.getName() + "#" + method.getName() + " maps " + path);
          }
        }
      }
    }
  }

  private static String[] paths(Class<?> controller) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    return mapping == null || mapping.path().length == 0 ? new String[] {""} : mapping.path();
  }

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
