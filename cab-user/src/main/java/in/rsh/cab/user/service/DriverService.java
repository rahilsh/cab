package in.rsh.cab.user.service;

import in.rsh.cab.commons.model.Driver;
import in.rsh.cab.user.repository.GenericRepositoryImpl;
import in.rsh.cab.user.repository.RepositoryFactory;
import in.rsh.cab.user.util.IDUtil;

public class DriverService {

  private final GenericRepositoryImpl<Driver> store;

  public DriverService() {
    store = RepositoryFactory.getInstance().getRepository(Driver.class);
  }

  public Driver registerDriver(Driver driver) {
    String driverId = IDUtil.generateID();
    Driver newDriver = driver.toBuilder().personId(driverId).build();
    store.put(driverId, newDriver);
    return newDriver;
  }

  public Driver updateDriver(Driver driver) {
    store.put(driver.getPersonId(), driver);
    return store.get(driver.getPersonId());
  }

  public Driver getDriver(String driverId) {
    return store.get(driverId);
  }
}
