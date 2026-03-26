package in.rsh.cab.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LocalDateTimeDeserializer implements DateTimeAdapter<LocalDateTime> {

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("d::MMM::uuuu HH::mm::ss").withLocale(Locale.ENGLISH);

  @Override
  public LocalDateTime deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    return LocalDateTime.parse(json.getAsString(), formatter);
  }

  @Override
  public JsonElement serialize(
      LocalDateTime localDateTime, Type srcType, JsonSerializationContext context) {
    return new JsonPrimitive(formatter.format(localDateTime));
  }

  @Override
  public Type getSupportedType() {
    return LocalDateTime.class;
  }
}
