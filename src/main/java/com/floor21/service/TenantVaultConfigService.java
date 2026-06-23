package com.floor21.service;

import com.floor21.security.Floor21UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Who may configure vault access grants for a tenant project. */
@Service
@RequiredArgsConstructor
public class TenantVaultConfigService {

    public boolean canCurrentUserManageTenantVaultConfig() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return false;
        }
        if (principal.isSuperAdmin()) {
            return false;
        }
        if (principal.getBuilderId() == null) {
            return false;
        }
        return principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUILDER_ADMIN".equals(a.getAuthority()));
    }
}
