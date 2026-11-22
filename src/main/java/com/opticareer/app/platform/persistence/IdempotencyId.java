package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

@Embeddable
public class IdempotencyId implements Serializable {
  @java.io.Serial private static final long serialVersionUID = 1L;

  @Column(name = "owner_id", nullable = false)
  public UUID ownerId;

  @Column(nullable = false, length = 120)
  public String scope;

  @Column(nullable = false, length = 100)
  public String key;

  public IdempotencyId() {}

  public IdempotencyId(UUID ownerId, String scope, String key) {
    this.ownerId = ownerId;
    this.scope = scope;
    this.key = key;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof IdempotencyId i
        && Objects.equals(ownerId, i.ownerId)
        && Objects.equals(scope, i.scope)
        && Objects.equals(key, i.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownerId, scope, key);
  }
}
