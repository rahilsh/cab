package in.r.cab.user.service;

import in.r.cab.user.model.Rider;
import in.r.cab.user.store.GenericStore;
import in.r.cab.user.store.StoreFactory;
import in.r.cab.user.util.IDUtil;

public class RiderService {

  private final GenericStore<Rider> store;

  public RiderService() {
    store = StoreFactory.getInstance().getStore(Rider.class);
  }

  public Rider registerRider(Rider rider) {
    String riderId = IDUtil.generateID();
    Rider newRider = rider.toBuilder().personId(riderId).build();
    store.put(riderId, newRider);
    return newRider;
  }
  public Rider getRider(String riderId) {
    return store.get(riderId);
  }
}
