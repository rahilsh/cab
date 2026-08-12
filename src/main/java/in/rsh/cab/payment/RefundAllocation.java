package in.rsh.cab.payment;

import java.math.BigInteger;

public record RefundAllocation(long driverShareMinor, long platformShareMinor) {

  public static RefundAllocation cumulative(
      long capturedMinor, long capturedDriverShareMinor, long refundedMinor,
      long refundedDriverShareMinor, long refundMinor) {
    long cumulativeRefund = Math.addExact(refundedMinor, refundMinor);
    long cumulativeDriver = BigInteger.valueOf(capturedDriverShareMinor)
        .multiply(BigInteger.valueOf(cumulativeRefund))
        .divide(BigInteger.valueOf(capturedMinor)).longValueExact();
    long driverShare = Math.subtractExact(cumulativeDriver, refundedDriverShareMinor);
    return new RefundAllocation(driverShare, Math.subtractExact(refundMinor, driverShare));
  }
}
