package in.rsh.cab.payment.internal.web;

import in.rsh.cab.payment.DriverEarning;
import in.rsh.cab.payment.Payment;
import in.rsh.cab.payment.PaymentService;
import in.rsh.cab.payment.ProviderCallbackService;
import in.rsh.cab.payment.Refund;
import in.rsh.cab.payment.SettlementBatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

  private final PaymentService payments;
  private final ProviderCallbackService callbacks;

  public PaymentController(PaymentService payments, ProviderCallbackService callbacks) {
    this.payments = payments;
    this.callbacks = callbacks;
  }

  @GetMapping("/api/v1/payments/{id}")
  public Payment get(@PathVariable UUID id) {
    return payments.getOwn(id);
  }

  @GetMapping("/api/v1/rides/{rideId}/payment")
  public Payment getByRide(@PathVariable UUID rideId) {
    return payments.getOwnByRide(rideId);
  }

  @PostMapping("/api/v1/finance/payments/{paymentId}/refunds")
  public ResponseEntity<Refund> refund(
      @PathVariable UUID paymentId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    PaymentService.RefundCreation result = payments.refund(
        idempotencyKey, paymentId, request.amountMinor(), request.reason());
    Refund refund = result.refund();
    return ResponseEntity.created(URI.create("/api/v1/payments/" + paymentId + "/refunds/"
        + refund.id())).header("Idempotent-Replayed", Boolean.toString(result.replayed())).body(refund);
  }

  @GetMapping("/api/v1/finance/refunds/{refundId}")
  public Refund refund(@PathVariable UUID refundId) {
    return payments.getRefund(refundId);
  }

  @GetMapping("/api/v1/driver/earnings")
  public List<DriverEarning> earnings() {
    return payments.ownEarnings();
  }

  @PostMapping("/api/v1/finance/settlements")
  public ResponseEntity<SettlementBatch> settle(
      @Valid @RequestBody SettlementRequest request) {
    SettlementBatch batch = payments.settle(request.currency());
    return ResponseEntity.created(URI.create("/api/v1/finance/settlements/" + batch.id()))
        .body(batch);
  }

  @GetMapping("/api/v1/finance/settlements")
  public List<SettlementBatch> settlements() {
    return payments.settlements();
  }

  @PostMapping("/api/v1/payment-providers/{provider}/accounts/{accountId}/events")
  public ProviderCallbackService.Result callback(
      @PathVariable String provider,
      @PathVariable UUID accountId,
      @RequestHeader("X-Provider-Timestamp") long timestamp,
      @RequestHeader("X-Provider-Signature") String signature,
      @RequestBody String rawBody) {
    return callbacks.process(accountId, provider, Instant.ofEpochSecond(timestamp), signature, rawBody);
  }

  public record RefundRequest(
      @Positive long amountMinor, @NotBlank @Size(max = 500) String reason) {}

  public record SettlementRequest(
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
}
