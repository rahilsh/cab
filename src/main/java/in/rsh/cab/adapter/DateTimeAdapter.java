package in.rsh.cab.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public interface DateTimeAdapter<T> extends JsonSerializer<T>, JsonDeserializer<T> {

  Type getSupportedType();
}