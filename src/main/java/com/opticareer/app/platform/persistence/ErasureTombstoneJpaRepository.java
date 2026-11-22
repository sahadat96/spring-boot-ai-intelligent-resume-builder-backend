package com.opticareer.app.platform.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ErasureTombstoneJpaRepository
    extends JpaRepository<ErasureTombstoneEntity, String> {
  @Modifying
  @Query("delete from PlatformErasureTombstone t where t.completedAt<:cutoff")
  void deleteBefore(@Param("cutoff") Instant cutoff);
}
