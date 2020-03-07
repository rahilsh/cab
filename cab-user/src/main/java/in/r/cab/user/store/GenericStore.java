package in.r.cab.user.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenericStore<T> {
  private final Map<Object, T> store = new HashMap<>();

  public T get(Object id) {
    return store.get(id);
  }

  public List<T> getAll() {
    return store.values().parallelStream().collect(Collectors.toList());
  }

  public void update(Object id, T object) {
    put(id, object);
  }

  public void put(Object id, T object) {
    store.put(id, object);
  }
}
