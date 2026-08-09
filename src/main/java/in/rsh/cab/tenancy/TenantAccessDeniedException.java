package in.rsh.cab.tenancy;

public class TenantAccessDeniedException extends RuntimeException {
  public TenantAccessDeniedException(String message) {
    super(message);
  }
}
