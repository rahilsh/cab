package in.rsh.cab.operations;

import in.rsh.cab.payment.PaymentOperationWorker;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class PaymentOutboxConsumer implements OutboxConsumer {

  private final PaymentOperationWorker worker;

  public PaymentOutboxConsumer(PaymentOperationWorker worker) {
    this.worker = worker;
  }

  @Override
  public boolean process(OutboxEvent event) {
    return worker.process(event);
  }
}
