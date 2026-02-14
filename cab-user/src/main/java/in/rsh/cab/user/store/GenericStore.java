package in.rsh.cab.user.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericStore<T> {
  private final Map<Object, T> store = new ConcurrentHashMap<>();

  public T get(Object id) {
    return store.get(id);
  }

  public List<T> getAll() {
    return new ArrayList<>(store.values());
  }

  public void update(Object id, T object) {
    put(id, object);
  }

  public void put(Object id, T object) {
    store.put(id, object);
  }
}
