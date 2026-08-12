package in.rsh.cab.pricing;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(long minorUnits, String currency) {

  public Money {
    Objects.requireNonNull(currency, "Currency is required");
    currency = currency.toUpperCase(Locale.ROOT);
    if (currency.length() != 3) {
      throw new IllegalArgumentException("Currency must be a three-letter ISO code");
    }
    try {
      Currency.getInstance(currency);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Currency must be a valid ISO code");
    }
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
  }

  public Money multiply(long multiplier) {
    return new Money(Math.multiplyExact(minorUnits, multiplier), currency);
  }

  public Money multiplyRatio(long numerator, long denominator, RoundingMode roundingMode) {
    if (numerator < 0 || denominator <= 0) {
      throw new IllegalArgumentException("Ratio must be non-negative with a positive denominator");
    }
    Objects.requireNonNull(roundingMode, "Rounding mode is required");
    BigInteger product = BigInteger.valueOf(minorUnits).multiply(BigInteger.valueOf(numerator));
    BigInteger[] result = product.divideAndRemainder(BigInteger.valueOf(denominator));
    if (result[1].signum() != 0 && shouldRoundAwayFromZero(product, result[1], denominator, roundingMode)) {
      result[0] = result[0].add(BigInteger.valueOf(product.signum()));
    }
    return new Money(result[0].longValueExact(), currency);
  }

  private boolean shouldRoundAwayFromZero(
      BigInteger product, BigInteger remainder, long denominator, RoundingMode roundingMode) {
    return switch (roundingMode) {
      case UP -> true;
      case DOWN -> false;
      case CEILING -> product.signum() > 0;
      case FLOOR -> product.signum() < 0;
      case HALF_UP -> remainder.abs().shiftLeft(1).compareTo(BigInteger.valueOf(denominator)) >= 0;
      case HALF_DOWN -> remainder.abs().shiftLeft(1).compareTo(BigInteger.valueOf(denominator)) > 0;
      case HALF_EVEN -> {
        int comparison = remainder.abs().shiftLeft(1).compareTo(BigInteger.valueOf(denominator));
        yield comparison > 0 || (comparison == 0 && resultIsOdd(product, denominator));
      }
      case UNNECESSARY -> throw new ArithmeticException("Rounding necessary");
    };
  }

  private boolean resultIsOdd(BigInteger product, long denominator) {
    return product.divide(BigInteger.valueOf(denominator)).testBit(0);
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "Money is required");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Currencies must match");
    }
  }
}
