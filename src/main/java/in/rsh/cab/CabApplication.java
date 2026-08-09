package in.rsh.cab;

import in.rsh.cab.config.MigrationApplication;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CabApplication {

  public static void main(String[] args) {
    Class<?> application =
        Arrays.asList(args).contains("--app.migration=true")
            ? MigrationApplication.class
            : CabApplication.class;
    SpringApplication.run(application, args);
  }
}
