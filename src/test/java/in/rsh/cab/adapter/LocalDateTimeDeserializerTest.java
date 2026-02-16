package in.rsh.cab.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocalDateTimeDeserializerTest {

  private final LocalDateTimeDeserializer deserializer = new LocalDateTimeDeserializer();

  @Test
  void deserialize_shouldParseDateTimeString() {
    String json = "\"17::Feb::2026 02::03::05\"";
    JsonElement jsonElement = JsonParser.parseString(json);

    LocalDateTime result = deserializer.deserialize(jsonElement, LocalDateTime.class, null);

    assertNotNull(result);
    assertEquals(2026, result.getYear());
    assertEquals(2, result.getMonthValue());
    assertEquals(17, result.getDayOfMonth());
    assertEquals(2, result.getHour());
    assertEquals(3, result.getMinute());
    assertEquals(5, result.getSecond());
  }
}
