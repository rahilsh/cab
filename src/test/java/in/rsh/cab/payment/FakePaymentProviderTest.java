package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTest {

  @Test
  void signsAndVerifiesExactTimestampAndBody() {
    FakePaymentProvider provider = new FakePaymentProvider("env:SECRET", "test-secret");
    PaymentAccount account = new PaymentAccount(UUID.randomUUID(), UUID.randomUUID(), "fake",
        "env:ACCOUNT", "env:SECRET", true);
    Instant timestamp = Instant.parse("2026-08-08T10:00:00Z");
    String body = "{\"eventId\":\"evt-1\"}";

    String signature = provider.sign(timestamp, body);

    assertTrue(provider.verifies(account, timestamp, body, signature));
    assertFalse(provider.verifies(account, timestamp.plusSeconds(1), body, signature));
    assertFalse(provider.verifies(account, timestamp, body + " ", signature));
    assertFalse(provider.verifies(account, timestamp, body, "invalid"));
    assertFalse(provider.verifies(new PaymentAccount(account.id(), account.tenantId(), "fake",
        account.configReference(), "env:OTHER", true), timestamp, body, signature));
  }
}
