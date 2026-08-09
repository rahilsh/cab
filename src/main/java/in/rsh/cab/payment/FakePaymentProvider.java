package in.rsh.cab.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentProvider implements PaymentProvider {

  private final String secretReference;
  private final byte[] webhookSecret;

  public FakePaymentProvider(
      @Value("${payments.fake.webhook-secret-reference}") String secretReference,
      @Value("${payments.fake.webhook-secret}") String webhookSecret) {
    this.secretReference = secretReference;
    this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public Submission capture(
      PaymentAccount account, UUID paymentId, long amountMinor, String currency,
      String idempotencyKey) {
    return new Submission("fake-pay-" + paymentId, "fake-request-" + idempotencyKey);
  }

  @Override
  public Submission refund(
      PaymentAccount account, UUID paymentId, UUID refundId, String providerPaymentId,
      long amountMinor, String currency, String idempotencyKey) {
    return new Submission("fake-refund-" + refundId, "fake-request-" + idempotencyKey);
  }

  @Override
  public Submission payout(
      PaymentAccount account, UUID payoutId, UUID driverId, long amountMinor, String currency,
      String idempotencyKey) {
    return new Submission("fake-payout-" + payoutId, "fake-request-" + idempotencyKey);
  }

  @Override
  public boolean verifies(
      PaymentAccount account, Instant timestamp, String rawBody, String signature) {
    if (!secretReference.equals(account.webhookSecretReference()) || signature == null) {
      return false;
    }
    byte[] expected = hmac(timestamp.getEpochSecond() + "." + rawBody);
    try {
      return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public String sign(Instant timestamp, String rawBody) {
    return HexFormat.of().formatHex(hmac(timestamp.getEpochSecond() + "." + rawBody));
  }

  private byte[] hmac(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot calculate webhook signature", exception);
    }
  }
}
