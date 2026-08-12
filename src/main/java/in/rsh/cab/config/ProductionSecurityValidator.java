package in.rsh.cab.config;

import in.rsh.cab.payment.PaymentProvider;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@DependsOnDatabaseInitialization
public class ProductionSecurityValidator implements ApplicationRunner {

  private final JdbcClient jdbc;

  public ProductionSecurityValidator(
      JdbcClient jdbc,
      @Value("${spring.datasource.username}") String runtimeUser,
      @Value("${spring.flyway.user}") String migrationUser,
      @Value("${payments.provider}") String paymentProvider,
      List<PaymentProvider> paymentProviders,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
    validate(
        runtimeUser,
        migrationUser,
        paymentProvider,
        paymentProviders.stream().map(PaymentProvider::name).toList(),
        issuer,
        jwkSetUri);
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    Boolean privileged =
        jdbc.sql(
                """
                SELECT rolsuper OR rolbypassrls
                FROM pg_roles
                WHERE rolname = current_user
                """)
            .query(Boolean.class)
            .single();
    if (Boolean.TRUE.equals(privileged)) {
      throw new IllegalStateException(
          "Production runtime database role must not be superuser or BYPASSRLS");
    }
    Boolean ownsTables =
        jdbc.sql(
                """
                SELECT EXISTS (
                  SELECT 1 FROM pg_class
                  WHERE relnamespace = 'public'::regnamespace
                    AND relkind IN ('r', 'p')
                    AND relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
                )
                """)
            .query(Boolean.class)
            .single();
    if (Boolean.TRUE.equals(ownsTables)) {
      throw new IllegalStateException(
          "Production runtime database role must not own application tables");
    }
  }

  static void validate(
      String runtimeUser,
      String migrationUser,
      String paymentProvider,
      List<String> availablePaymentProviders,
      String issuer,
      String jwkSetUri) {
    if (runtimeUser.isBlank()) {
      throw new IllegalStateException("Production runtime database user is required");
    }
    if (migrationUser.isBlank()) {
      throw new IllegalStateException("Production migration database user is required");
    }
    if (runtimeUser.equals(migrationUser)) {
      throw new IllegalStateException(
          "Production migration and runtime database users must be different");
    }
    if (paymentProvider.isBlank() || "fake".equals(paymentProvider)) {
      throw new IllegalStateException("Production requires a non-fake payment provider");
    }
    if (!availablePaymentProviders.contains(paymentProvider)) {
      throw new IllegalStateException(
          "No PaymentProvider bean is available for production provider " + paymentProvider);
    }
    requireHttps("OIDC issuer", issuer);
    requireHttps("OIDC JWK set URI", jwkSetUri);
  }

  private static void requireHttps(String name, String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(name + " must be a valid HTTPS URI", exception);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalStateException(name + " must be a valid HTTPS URI");
    }
  }
}
