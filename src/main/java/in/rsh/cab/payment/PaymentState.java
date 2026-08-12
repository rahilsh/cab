package in.rsh.cab.payment;

public enum PaymentState {
  CREATED,
  AUTHORIZATION_PENDING,
  AUTHORIZED,
  CAPTURE_PENDING,
  CAPTURED,
  FAILED,
  VOIDED
}
