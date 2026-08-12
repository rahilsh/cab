package in.rsh.cab.payment;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import in.rsh.cab.tenancy.TenantExecution;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PaymentOperationWorker {

  private final PaymentRepository payments;
  private final Map<String, PaymentProvider> providers;
  private final TenantExecution tenantExecution;
  private final Clock clock;

  public PaymentOperationWorker(
      PaymentRepository payments, List<PaymentProvider> providers,
      TenantExecution tenantExecution, Clock clock) {
    this.payments = payments;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
        PaymentProvider::name, Function.identity()));
    this.tenantExecution = tenantExecution;
    this.clock = clock;
  }

  public boolean process(OutboxEvent event) {
    if ("payment.capture_requested".equals(event.eventType())) {
      return capture(event);
    }
    if ("payment.refund_requested".equals(event.eventType())) {
      return refund(event);
    }
    if ("payout.requested".equals(event.eventType())) {
      return payout(event);
    }
    return false;
  }

  private boolean capture(OutboxEvent event) {
    Payment payment = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.find(event.tenantId(), event.aggregateId()).orElse(null));
    if (payment == null || !tenantExecution.inTransaction(event.tenantId(),
        () -> payments.markCaptureProcessing(event.tenantId(), payment.id()))) {
      return false;
    }
    PaymentAccount account = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.findAccountForPayment(event.tenantId(), payment.id()).orElseThrow());
    try {
      PaymentProvider.Submission submitted = provider(account).capture(account, payment.id(),
          payment.amountMinor(), payment.currency(), "capture:" + payment.id());
      tenantExecution.inTransaction(event.tenantId(), () -> payments.markCaptureSubmitted(
          event.tenantId(), payment.id(), submitted.providerObjectId(), submitted.providerRequestId()));
      return true;
    } catch (RuntimeException exception) {
      tenantExecution.inTransaction(event.tenantId(), () -> payments.markCaptureSubmissionRetryable(
          event.tenantId(), payment.id(), "PROVIDER_UNAVAILABLE"));
      throw exception;
    }
  }

  private boolean refund(OutboxEvent event) {
    Refund refund = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.findRefund(event.tenantId(), event.aggregateId()).orElse(null));
    if (refund == null || !tenantExecution.inTransaction(event.tenantId(),
        () -> payments.markRefundProcessing(event.tenantId(), refund.id()))) {
      return false;
    }
    Payment payment = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.find(event.tenantId(), refund.paymentId()).orElseThrow());
    PaymentAccount account = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.findAccountForPayment(event.tenantId(), payment.id()).orElseThrow());
    try {
      PaymentProvider.Submission submitted = provider(account).refund(account, payment.id(),
          refund.id(), payment.providerPaymentId(), refund.amountMinor(), refund.currency(),
          "refund:" + refund.id());
      tenantExecution.inTransaction(event.tenantId(), () -> payments.markRefundSubmitted(
          event.tenantId(), refund.id(), submitted.providerObjectId(), clock.instant()));
      return true;
    } catch (RuntimeException exception) {
      throw exception;
    }
  }

  private boolean payout(OutboxEvent event) {
    SettlementBatch.Payout payout = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.findPayout(event.tenantId(), event.aggregateId()).orElse(null));
    if (payout == null || !tenantExecution.inTransaction(event.tenantId(),
        () -> payments.markPayoutProcessing(event.tenantId(), payout.id()))) {
      return false;
    }
    PaymentAccount account = tenantExecution.inTransaction(event.tenantId(),
        () -> payments.findAccount(payout.paymentAccountId()).orElseThrow());
    try {
      PaymentProvider.Submission submitted = provider(account).payout(account, payout.id(),
          payout.driverId(), payout.amountMinor(), payout.currency(), "payout:" + payout.id());
      tenantExecution.inTransaction(event.tenantId(), () -> payments.markPayoutSubmitted(
          event.tenantId(), payout.id(), submitted.providerObjectId(),
          submitted.providerRequestId(), clock.instant()));
      return true;
    } catch (RuntimeException exception) {
      tenantExecution.inTransaction(event.tenantId(), () -> payments.markPayoutSubmissionRetryable(
          event.tenantId(), payout.id(), "PROVIDER_UNAVAILABLE"));
      throw exception;
    }
  }

  private PaymentProvider provider(PaymentAccount account) {
    PaymentProvider provider = providers.get(account.provider());
    if (provider == null) {
      throw new IllegalStateException("Payment provider is not configured: " + account.provider());
    }
    return provider;
  }
}
