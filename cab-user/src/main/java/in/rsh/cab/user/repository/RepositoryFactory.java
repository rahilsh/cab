package in.rsh.cab.user.repository;

import in.rsh.cab.commons.model.Booking;
import in.rsh.cab.commons.model.Cab;
import in.rsh.cab.commons.model.Driver;
import in.rsh.cab.user.model.Rider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RepositoryFactory {
  private static final RepositoryFactory INSTANCE = new RepositoryFactory();
  private final Map<Class<?>, GenericRepositoryImpl<?>> storeMapping = new ConcurrentHashMap<>();

  private RepositoryFactory() {
    storeMapping.put(Rider.class, new GenericRepositoryImpl<Rider>());
    storeMapping.put(Driver.class, new GenericRepositoryImpl<Driver>());
    storeMapping.put(Cab.class, new GenericRepositoryImpl<Cab>());
    storeMapping.put(Booking.class, new GenericRepositoryImpl<Booking>());
  }

  public static RepositoryFactory getInstance() {
    return INSTANCE;
  }

  public <T> GenericRepositoryImpl<T> getRepository(Class<T> t) {
    return (GenericRepositoryImpl<T>) storeMapping.get(t);
  }
}
