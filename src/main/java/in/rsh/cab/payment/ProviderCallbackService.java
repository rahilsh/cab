package in.rsh.cab.payment;

import in.rsh.cab.operations.OutboxService;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProviderCallbackService {

  private final PaymentRepository payments;
  private final Map<String, PaymentProvider> providers;
  private final OutboxService outbox;
  private final ObjectMapper json;
  private final Clock clock;
  private final Duration tolerance;
  private final int commissionBasisPoints;

  public ProviderCallbackService(
      PaymentRepository payments, List<PaymentProvider> providers, OutboxService outbox,
      ObjectMapper json, Clock clock,
      @Value("${payments.webhook-tolerance}") Duration tolerance,
      @Value("${payments.platform-commission-basis-points:1500}") int commissionBasisPoints) {
    if (commissionBasisPoints < 0 || commissionBasisPoints > 10_000) {
      throw new IllegalArgumentException("Platform commission must be between 0 and 10000 basis points");
    }
    this.payments = payments;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
        PaymentProvider::name, Function.identity()));
    this.outbox = outbox;
    this.json = json;
    this.clock = clock;
    this.tolerance = tolerance;
    this.commissionBasisPoints = commissionBasisPoints;
  }

  @Transactional
  public Result process(
      UUID accountId, String providerName, Instant timestamp, String signature, String rawBody) {
    Instant now = clock.instant();
    if (timestamp.isBefore(now.minus(tolerance)) || timestamp.isAfter(now.plus(tolerance))) {
      throw new PaymentSignatureException("Webhook timestamp is outside the replay window");
    }
    PaymentAccount account = payments.findAccount(accountId)
        .filter(value -> value.provider().equals(providerName))
        .orElseThrow(() -> new PaymentSignatureException("Payment account is not available"));
    PaymentProvider provider = providers.get(providerName);
    if (provider == null || !provider.verifies(account, timestamp, rawBody, signature)) {
      throw new PaymentSignatureException("Webhook signature is invalid");
    }
    ProviderEvent event;
    try {
      event = json.readValue(rawBody, ProviderEvent.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Webhook body is invalid", exception);
    }
    if (!payments.insertProviderEvent(UUID.randomUUID(), account, event, now)) {
      return new Result(false, false);
    }
    boolean applied = switch (event.type()) {
      case CAPTURE_SUCCEEDED -> applyCapture(account, event, now, true);
      case CAPTURE_FAILED -> applyCapture(account, event, now, false);
      case REFUND_SUCCEEDED -> applyRefund(account, event, now, true);
      case REFUND_FAILED -> applyRefund(account, event, now, false);
    };
    payments.markProviderEvent(account.id(), event.eventId(), applied, now);
    return new Result(true, applied);
  }

  private boolean applyCapture(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded) {
    if (event.paymentId() == null || event.refundId() != null
        || (succeeded && (event.amountMinor() == null || event.currency() == null))) {
      return false;
    }
    boolean applied = payments.applyCaptureEvent(account, event, now, succeeded);
    if (applied && succeeded) {
      payments.postCaptureLedger(
          account.tenantId(), event.paymentId(), commissionBasisPoints, now);
    }
    if (applied) {
      outbox.append(account.tenantId(), "payment", event.paymentId(), event.providerVersion(),
          succeeded ? "payment.captured" : "payment.capture_failed", 1,
          json.valueToTree(new PaymentResult(event.paymentId(), event.amountMinor(), event.currency())),
          null);
    }
    return applied;
  }

  private boolean applyRefund(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded) {
    if (event.refundId() == null || event.paymentId() == null
        || (succeeded && (event.amountMinor() == null || event.currency() == null))) {
      return false;
    }
    boolean applied = payments.applyRefundEvent(account, event, now, succeeded);
    if (applied && succeeded) {
      payments.postRefundLedger(
          account.tenantId(), event.refundId(), commissionBasisPoints, now);
    }
    if (applied) {
      outbox.append(account.tenantId(), "refund", event.refundId(), event.providerVersion(),
          succeeded ? "payment.refunded" : "payment.refund_failed", 1,
          json.valueToTree(new PaymentResult(event.paymentId(), event.amountMinor(), event.currency())),
          null);
    }
    return applied;
  }

  public record Result(boolean accepted, boolean applied) {}

  private record PaymentResult(UUID paymentId, Long amountMinor, String currency) {}
}
