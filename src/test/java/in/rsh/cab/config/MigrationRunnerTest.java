package in.rsh.cab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

class MigrationRunnerTest {

  @Test
  void closesApplicationAfterMigrationsComplete() throws Exception {
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());

    new MigrationRunner(context).run(null);

    verify(context).close();
  }

  @Test
  void migrationProfileActivatesRunnerWithoutScheduling() {
    new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("migration"))
        .withPropertyValues("app.scheduling.enabled=false")
        .withUserConfiguration(MigrationRunner.class, SchedulingConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MigrationRunner.class);
              assertThat(context).doesNotHaveBean(SchedulingConfiguration.class);
            });
  }

  @Test
  void dedicatedMigrationApplicationIsConditional() {
    new ApplicationContextRunner()
        .withPropertyValues("app.migration=false")
        .withUserConfiguration(MigrationApplication.class)
        .run(context -> assertThat(context).doesNotHaveBean(MigrationApplication.class));
  }
}
