package in.rsh.cab.webhook;

public interface SecretResolver {
  String resolve(String reference);
}
