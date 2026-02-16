package in.rsh.cab.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "in.rsh.cab.commons.repository")
@EntityScan(basePackages = "in.rsh.cab.commons.entity")
public class CabAdminApplication {

  public static void main(String[] args) {
    SpringApplication.run(CabAdminApplication.class, args);
  }
}
