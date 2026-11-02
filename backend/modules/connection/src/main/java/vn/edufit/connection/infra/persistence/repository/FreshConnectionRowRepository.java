package vn.edufit.connection.infra.persistence.repository;

import java.util.Optional;
import java.util.UUID;

/** Repository fragment for loading a current row under a write lock. */
public interface FreshConnectionRowRepository {
  <T> Optional<T> lockFresh(Class<T> entityType, UUID id);
}
