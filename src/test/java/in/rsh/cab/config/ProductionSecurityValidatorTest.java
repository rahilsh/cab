package in.rsh.cab.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {

  @Test
  void acceptsSeparatedCredentialsSecretAndHttpsOidc() {
    assertDoesNotThrow(
        () ->
            ProductionSecurityValidator.validate(
                "cab_app",
                "cab_migration",
                "strong-secret",
                "https://identity.example/realms/cab",
                "https://identity.example/realms/cab/certs"));
  }

  @Test
  void rejectsDevelopmentSecretInsecureOidcAndEqualUsers() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "cab", "cab", "strong-secret", "https://issuer.example", "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "local-development-only",
                "https://issuer.example",
                "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "secret",
                "http://issuer.example",
                "https://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "migration",
                "secret",
                "https://issuer.example",
                "http://issuer.example/certs"));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionSecurityValidator.validate(
                "app",
                "",
                "secret",
                "https://issuer.example",
                "https://issuer.example/certs"));
  }
}
