package in.rsh.cab.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentSecretResolverTest {

  @Test
  void resolvesOnlyCabWebhookNamespace() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("CAB_WEBHOOK_PARTNER", "secret");
    EnvironmentSecretResolver resolver = new EnvironmentSecretResolver(environment);

    assertEquals("secret", resolver.resolve("env:CAB_WEBHOOK_PARTNER"));
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve("env:PATH"));
    assertThrows(IllegalStateException.class, () -> resolver.resolve("env:CAB_WEBHOOK_MISSING"));
  }
}
