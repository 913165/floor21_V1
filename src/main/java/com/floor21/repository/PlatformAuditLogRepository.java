package com.floor21.repository;

import com.floor21.entity.PlatformAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, UUID> {

    List<PlatformAuditLog> findTop100ByOrderByCreatedAtDesc();

    List<PlatformAuditLog> findTop50ByBuilderIdOrderByCreatedAtDesc(UUID builderId);
}
