package in.rsh.cab.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migration")
public class MigrationRunner implements ApplicationRunner {

  private final ConfigurableApplicationContext context;

  public MigrationRunner(ConfigurableApplicationContext context) {
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    SpringApplication.exit(context);
  }
}
