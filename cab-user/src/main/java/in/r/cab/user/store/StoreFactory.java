package in.r.cab.user.store;

import in.r.cab.user.model.Booking;
import in.r.cab.user.model.Cab;
import in.r.cab.user.model.Driver;
import in.r.cab.user.model.Rider;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class StoreFactory {
  Map<Type, GenericStore> storeMapping = new HashMap<>();

  private static StoreFactory storeFactory = null;

  public static StoreFactory getInstance() {
    if (storeFactory == null) storeFactory = new StoreFactory();
    return storeFactory;
  }

  private StoreFactory() {
    storeMapping.put(Rider.class, new GenericStore<Rider>());
    storeMapping.put(Driver.class, new GenericStore<Driver>());
    storeMapping.put(Cab.class, new GenericStore<Cab>());
    storeMapping.put(Booking.class, new GenericStore<Booking>());
  }

  public <T> GenericStore<T> getStore(Class<T> t) {
    return (GenericStore<T>) storeMapping.get(t);
  }
}
