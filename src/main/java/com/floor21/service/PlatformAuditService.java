package com.floor21.service;

import com.floor21.entity.PlatformAuditLog;
import com.floor21.repository.PlatformAuditLogRepository;
import com.floor21.security.Floor21UserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAuditService {

    private final PlatformAuditLogRepository auditLogRepository;

    @Transactional
    public void log(String action, String entityType, String entityId, UUID builderId, String details) {
        PlatformAuditLog row = new PlatformAuditLog();
        row.setActorEmail(currentActorEmail());
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setBuilderId(builderId);
        row.setDetails(details);
        auditLogRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<PlatformAuditLog> recent() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    private static String currentActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Floor21UserPrincipal principal) {
            return principal.getEmail();
        }
        return auth != null ? auth.getName() : "system";
    }
}
