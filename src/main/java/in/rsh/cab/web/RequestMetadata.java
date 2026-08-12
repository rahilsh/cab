package in.rsh.cab.web;

public record RequestMetadata(String correlationId) {

  private static final ThreadLocal<RequestMetadata> CURRENT = new ThreadLocal<>();

  public static String correlationIdOrNull() {
    RequestMetadata metadata = CURRENT.get();
    return metadata == null ? null : metadata.correlationId();
  }

  static void set(RequestMetadata metadata) {
    CURRENT.set(metadata);
  }

  static void clear() {
    CURRENT.remove();
  }
}
