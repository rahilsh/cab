package in.r.cab.user.service;

import in.r.cab.user.model.Location;
import in.r.cab.user.model.Cab;
import in.r.cab.user.model.Cab.CabStatus;
import in.r.cab.user.store.GenericStore;
import in.r.cab.user.store.StoreFactory;
import in.r.cab.user.util.IDUtil;
import java.util.Optional;

public class CabService {

  private final GenericStore<Cab> store;

  public CabService() {
    store = StoreFactory.getInstance().getStore(Cab.class);
  }

  public Cab registerCab(Cab cab) {
    String cabId = IDUtil.generateID();
    Cab newCab = cab.toBuilder().cabId(cabId).status(CabStatus.AVAILABLE).build();
    store.put(cabId, newCab);
    return newCab;
  }

  public Cab updateCab(Cab cab) {
    store.put(cab.getCabId(), cab);
    return store.get(cab.getCabId());
  }

  public boolean updateCabLocation(String cabId, Location location) {
    Cab updatedCab = store.get(cabId).toBuilder().location(location).build();
    updateCab(updatedCab);
    return true;
  }

  public Optional<Cab> getCabByDriver(String driverId) {
    return store.getAll().stream().filter(cab -> cab.getDriverId().equals(driverId)).findFirst();
  }

  public boolean updateCabStatus(String cabId, CabStatus cabStatus) {
    Cab updatedCab = store.get(cabId).toBuilder().status(cabStatus).build();
    updateCab(updatedCab);
    return true;
  }
}
