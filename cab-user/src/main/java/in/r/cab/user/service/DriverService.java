package in.r.cab.user.service;

import in.r.cab.user.model.Driver;
import in.r.cab.user.store.GenericStore;
import in.r.cab.user.store.StoreFactory;
import in.r.cab.user.util.IDUtil;

public class DriverService {

  private final GenericStore<Driver> store;

  public DriverService() {
    store = StoreFactory.getInstance().getStore(Driver.class);
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

  public Driver getDriver(String driverId){
    return store.get(driverId);
  }


}
