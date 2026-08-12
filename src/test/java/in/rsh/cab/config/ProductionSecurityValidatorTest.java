package in.rsh.cab.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {

  @Test
  void acceptsSeparatedCredentialsConfiguredProviderAndHttpsOidc() {
    assertDoesNotThrow(
        () ->
            ProductionSecurityValidator.validate(
                "cab_app",
                "cab_migration",
                "stripe",
                java.util.List.of("stripe"),
                "https://identity.example/realms/cab",
                "https://identity.example/realms/cab/certs"));
  }

  @Test
  void rejectsFakeOrMissingProviderInsecureOidcAndEqualUsers() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "cab", "cab", "stripe", java.util.List.of("stripe"),
                "https://issuer.example", "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "fake",
                java.util.List.of(),
                "https://issuer.example",
                "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "stripe",
                java.util.List.of("stripe"),
                "http://issuer.example",
                "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "stripe",
                java.util.List.of("stripe"),
                "https://issuer.example",
                "http://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "",
                "stripe",
                java.util.List.of("stripe"),
                "https://issuer.example",
                "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app", "migration", "stripe", java.util.List.of(),
                "https://issuer.example", "https://issuer.example/certs"));
  }
}
