package in.rsh.cab.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void normalizesCurrencyAndPerformsExactArithmetic() {
    Money money = new Money(125, "usd");

    assertEquals("USD", money.currency());
    assertEquals(new Money(200, "USD"), money.add(new Money(75, "USD")));
    assertEquals(new Money(375, "USD"), money.multiply(3));
  }

  @Test
  void rejectsInvalidAndMixedCurrencies() {
    assertThrows(NullPointerException.class, () -> new Money(1, null));
    assertThrows(IllegalArgumentException.class, () -> new Money(1, "US"));
    assertThrows(IllegalArgumentException.class, () -> new Money(1, "ZZZ"));
    assertThrows(IllegalArgumentException.class,
        () -> new Money(1, "USD").add(new Money(1, "EUR")));
    assertThrows(NullPointerException.class, () -> new Money(1, "USD").add(null));
  }

  @Test
  void detectsAdditionMultiplicationAndRatioOverflow() {
    assertThrows(ArithmeticException.class,
        () -> new Money(Long.MAX_VALUE, "USD").add(new Money(1, "USD")));
    assertThrows(ArithmeticException.class,
        () -> new Money(Long.MAX_VALUE, "USD").multiply(2));
    assertThrows(ArithmeticException.class,
        () -> new Money(Long.MAX_VALUE, "USD").multiplyRatio(2, 1, RoundingMode.DOWN));
  }

  @Test
  void multipliesRatiosWithExplicitRounding() {
    Money value = new Money(5, "USD");

    assertEquals(3, value.multiplyRatio(1, 2, RoundingMode.HALF_UP).minorUnits());
    assertEquals(2, value.multiplyRatio(1, 2, RoundingMode.HALF_DOWN).minorUnits());
    assertEquals(2, value.multiplyRatio(1, 2, RoundingMode.HALF_EVEN).minorUnits());
    assertEquals(4, new Money(7, "USD").multiplyRatio(1, 2, RoundingMode.HALF_EVEN).minorUnits());
    assertEquals(3, value.multiplyRatio(1, 2, RoundingMode.UP).minorUnits());
    assertEquals(2, value.multiplyRatio(1, 2, RoundingMode.DOWN).minorUnits());
    assertEquals(3, value.multiplyRatio(1, 2, RoundingMode.CEILING).minorUnits());
    assertEquals(2, value.multiplyRatio(1, 2, RoundingMode.FLOOR).minorUnits());
    assertEquals(-2, new Money(-5, "USD").multiplyRatio(1, 2, RoundingMode.CEILING).minorUnits());
    assertEquals(-3, new Money(-5, "USD").multiplyRatio(1, 2, RoundingMode.FLOOR).minorUnits());
    assertEquals(5, value.multiplyRatio(2, 2, RoundingMode.UNNECESSARY).minorUnits());
    assertThrows(ArithmeticException.class,
        () -> value.multiplyRatio(1, 2, RoundingMode.UNNECESSARY));
  }

  @Test
  void validatesRatioArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> new Money(1, "USD").multiplyRatio(-1, 1, RoundingMode.DOWN));
    assertThrows(IllegalArgumentException.class,
        () -> new Money(1, "USD").multiplyRatio(1, 0, RoundingMode.DOWN));
    assertThrows(NullPointerException.class,
        () -> new Money(1, "USD").multiplyRatio(1, 1, null));
  }
}
