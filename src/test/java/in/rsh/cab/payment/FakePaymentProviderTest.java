package in.rsh.cab.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

  @Test
  void payoutUsesStableProviderIdentifiers() {
    FakePaymentProvider provider = new FakePaymentProvider("env:SECRET", "test-secret");
    PaymentAccount account = new PaymentAccount(UUID.randomUUID(), UUID.randomUUID(), "fake",
        "env:ACCOUNT", "env:SECRET", true);
    UUID payoutId = UUID.randomUUID();

    PaymentProvider.Submission result = provider.payout(
        account, payoutId, UUID.randomUUID(), 100, "USD", "payout:" + payoutId);

    assertEquals("fake-payout-" + payoutId, result.providerObjectId());
    assertEquals("fake-request-payout:" + payoutId, result.providerRequestId());
  }
}
