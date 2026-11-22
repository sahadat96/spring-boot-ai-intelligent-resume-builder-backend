package com.opticareer.app.platform.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL transaction locks for operations which must serialize before a row exists. */
@Component
public class DatabaseLocks {
  private final EntityManager entityManager;

  public DatabaseLocks(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void exclusive(String key) {
    entityManager
        .createNativeQuery(
            "select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))", Integer.class)
        .setParameter("key", key)
        .getSingleResult();
  }
}
