package com.floor21.interceptor;

import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.StaffBuildingAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final StaffBuildingAccessService staffBuildingAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            TenantContext.clear();
            return true;
        }
        if (principal.isSuperAdmin()) {
            TenantContext.clear();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(Floor21UserPrincipal.SESSION_BUILDER_ID);
            }
            return true;
        }
        UUID builderId = principal.getBuilderId();
        TenantContext.setBuilderId(builderId);
        if (principal.getStaffUserId() != null) {
            TenantContext.setAllowedBuildingIds(
                    staffBuildingAccessService.resolveAllowedBuildingIds(
                            principal.getStaffUserId(), builderId));
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(Floor21UserPrincipal.SESSION_BUILDER_ID, builderId.toString());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
