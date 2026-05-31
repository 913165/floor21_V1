package com.floor21.repository;

import com.floor21.entity.PlatformAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, UUID> {

    List<PlatformAuditLog> findTop100ByOrderByCreatedAtDesc();

    List<PlatformAuditLog> findTop50ByBuilderIdOrderByCreatedAtDesc(UUID builderId);

    @Modifying
    @Query("UPDATE PlatformAuditLog a SET a.builderId = null WHERE a.builderId = :builderId")
    void clearBuilderId(@Param("builderId") UUID builderId);
}
