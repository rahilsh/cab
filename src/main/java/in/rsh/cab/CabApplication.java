package in.rsh.cab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "in.rsh.cab.repository")
@EntityScan(basePackages = "in.rsh.cab.entity")
public class CabApplication {

  public static void main(String[] args) {
    SpringApplication.run(CabApplication.class, args);
  }
}
