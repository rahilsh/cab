package in.rsh.cab.webhook;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretResolver implements SecretResolver {

  private final Environment environment;

  public EnvironmentSecretResolver(Environment environment) {
    this.environment = environment;
  }

  @Override
  public String resolve(String reference) {
    if (reference == null || !reference.matches("^env:[A-Za-z_][A-Za-z0-9_]*$")) {
      throw new IllegalArgumentException("Only env secret references are supported");
    }
    String secret = environment.getProperty(reference.substring(4));
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Webhook secret is not configured");
    }
    return secret;
  }
}
