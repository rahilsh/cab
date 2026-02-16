package in.rsh.cab.commons.repository;

import java.util.Collection;
import java.util.Optional;

public interface Repository<T, ID> {

  T save(T entity);

  Optional<T> findById(ID id);

  Collection<T> findAll();

  void delete(ID id);

  boolean exists(ID id);
}
