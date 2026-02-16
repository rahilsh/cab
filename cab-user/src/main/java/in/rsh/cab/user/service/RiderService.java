package in.rsh.cab.user.service;

import in.rsh.cab.user.model.Rider;
import in.rsh.cab.user.repository.GenericRepositoryImpl;
import in.rsh.cab.user.repository.RepositoryFactory;
import in.rsh.cab.user.util.IDUtil;

public class RiderService {

  private final GenericRepositoryImpl<Rider> store;

  public RiderService() {
    store = RepositoryFactory.getInstance().getRepository(Rider.class);
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
