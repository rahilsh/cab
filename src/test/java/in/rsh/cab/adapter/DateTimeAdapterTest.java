package in.rsh.cab.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DateTimeAdapterTest {

  @Nested
  class LocalDateTimeSerializerTests {

    private final LocalDateTimeSerializer serializer = new LocalDateTimeSerializer();

    @Test
    void serialize_shouldFormatDateTime() {
      LocalDateTime dateTime = LocalDateTime.of(2026, 2, 17, 2, 3, 5);

      JsonElement result = serializer.serialize(dateTime, null, null);

      assertNotNull(result);
      assertEquals("17::Feb::2026 02::03::05", result.getAsString());
    }

    @Test
    void getSupportedType_shouldReturnLocalDateTime() {
      assertEquals(LocalDateTime.class, serializer.getSupportedType());
    }
  }

  @Nested
  class LocalDateTimeDeserializerTests {

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

    @Test
    void getSupportedType_shouldReturnLocalDateTime() {
      assertEquals(LocalDateTime.class, deserializer.getSupportedType());
    }
  }

  @Nested
  class RoundTripTests {

    private final LocalDateTimeSerializer serializer = new LocalDateTimeSerializer();
    private final LocalDateTimeDeserializer deserializer = new LocalDateTimeDeserializer();

    @Test
    void serialize_thenDeserialize_shouldReturnOriginalValue() {
      LocalDateTime original = LocalDateTime.of(2026, 2, 17, 2, 3, 5);

      JsonElement serialized = serializer.serialize(original, null, null);
      LocalDateTime deserialized = deserializer.deserialize(serialized, LocalDateTime.class, null);

      assertEquals(original, deserialized);
    }
  }
}
