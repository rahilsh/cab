package in.rsh.cab.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SettlementBatch(
    UUID id, String currency, String state, long totalMinor, Instant createdAt,
    List<Payout> payouts) {

  public record Payout(UUID id, UUID driverId, long amountMinor, String currency, String state) {}
}
