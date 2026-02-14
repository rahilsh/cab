package in.rsh.cab.user.store;

import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Driver;
import in.rsh.cab.user.model.Rider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoreFactory {
  private static final StoreFactory INSTANCE = new StoreFactory();
  private final Map<Class<?>, GenericStore<?>> storeMapping = new ConcurrentHashMap<>();

  private StoreFactory() {
    storeMapping.put(Rider.class, new GenericStore<Rider>());
    storeMapping.put(Driver.class, new GenericStore<Driver>());
    storeMapping.put(Cab.class, new GenericStore<Cab>());
    storeMapping.put(Booking.class, new GenericStore<Booking>());
  }

  public static StoreFactory getInstance() {
    return INSTANCE;
  }

  public <T> GenericStore<T> getStore(Class<T> t) {
    return (GenericStore<T>) storeMapping.get(t);
  }
}
