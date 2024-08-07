package in.rsh.cab.user.store;

import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Driver;
import in.rsh.cab.user.model.Rider;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class StoreFactory {
  private static StoreFactory storeFactory = null;
  Map<Type, GenericStore> storeMapping = new HashMap<>();

  private StoreFactory() {
    storeMapping.put(Rider.class, new GenericStore<Rider>());
    storeMapping.put(Driver.class, new GenericStore<Driver>());
    storeMapping.put(Cab.class, new GenericStore<Cab>());
    storeMapping.put(Booking.class, new GenericStore<Booking>());
  }

  public static StoreFactory getInstance() {
    if (storeFactory == null) storeFactory = new StoreFactory();
    return storeFactory;
  }

  public <T> GenericStore<T> getStore(Class<T> t) {
    return (GenericStore<T>) storeMapping.get(t);
  }
}
