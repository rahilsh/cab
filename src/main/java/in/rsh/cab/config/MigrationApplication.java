package in.rsh.cab.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class})
@ConditionalOnProperty(name = "app.migration", havingValue = "true")
@Import(MigrationRunner.class)
public class MigrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(MigrationApplication.class, args);
  }
}
