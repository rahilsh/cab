package in.rsh.cab.payment;

import in.rsh.cab.operations.OutboxEvent;
import in.rsh.cab.payment.internal.persistence.PaymentRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentOperationWorker {

  private final PaymentRepository payments;
  private final Map<String, PaymentProvider> providers;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public PaymentOperationWorker(
      PaymentRepository payments, List<PaymentProvider> providers,
      TransactionTemplate transactions, Clock clock) {
    this.payments = payments;
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
        PaymentProvider::name, Function.identity()));
    this.transactions = transactions;
    this.clock = clock;
  }

  public boolean process(OutboxEvent event) {
    if ("payment.capture_requested".equals(event.eventType())) {
      return capture(event);
    }
    if ("payment.refund_requested".equals(event.eventType())) {
      return refund(event);
    }
    return false;
  }

  private boolean capture(OutboxEvent event) {
    Payment payment = payments.find(event.tenantId(), event.aggregateId()).orElse(null);
    if (payment == null || !Boolean.TRUE.equals(transactions.execute(
        status -> payments.markCaptureProcessing(event.tenantId(), payment.id())))) {
      return false;
    }
    PaymentAccount account = payments.findAccountForPayment(event.tenantId(), payment.id())
        .orElseThrow();
    try {
      PaymentProvider.Submission submitted = provider(account).capture(account, payment.id(),
          payment.amountMinor(), payment.currency(), "capture:" + payment.id());
      transactions.executeWithoutResult(status -> payments.markCaptureSubmitted(event.tenantId(),
          payment.id(), submitted.providerObjectId(), submitted.providerRequestId()));
      return true;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> payments.markCaptureSubmissionFailed(
          event.tenantId(), payment.id(), "PROVIDER_UNAVAILABLE", clock.instant()));
      throw exception;
    }
  }

  private boolean refund(OutboxEvent event) {
    Refund refund = payments.findRefund(event.tenantId(), event.aggregateId()).orElse(null);
    if (refund == null || !Boolean.TRUE.equals(transactions.execute(
        status -> payments.markRefundProcessing(event.tenantId(), refund.id())))) {
      return false;
    }
    Payment payment = payments.find(event.tenantId(), refund.paymentId()).orElseThrow();
    PaymentAccount account = payments.findAccountForPayment(event.tenantId(), payment.id())
        .orElseThrow();
    PaymentProvider.Submission submitted = provider(account).refund(account, payment.id(),
        refund.id(), payment.providerPaymentId(), refund.amountMinor(), refund.currency(),
        "refund:" + refund.id());
    transactions.executeWithoutResult(status -> payments.markRefundSubmitted(
        event.tenantId(), refund.id(), submitted.providerObjectId(), clock.instant()));
    return true;
  }

  private PaymentProvider provider(PaymentAccount account) {
    PaymentProvider provider = providers.get(account.provider());
    if (provider == null) {
      throw new IllegalStateException("Payment provider is not configured: " + account.provider());
    }
    return provider;
  }
}
