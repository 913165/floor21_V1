package com.floor21.interceptor;

import com.floor21.security.TenantContext;
import com.floor21.security.VaultSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class VaultAccessInterceptor implements HandlerInterceptor {

    @Value("${floor21.vault.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith("/vault")) {
            return true;
        }
        if (path.equals("/vault/unlock")
                || path.startsWith("/vault/unlock/")
                || path.equals("/vault/reset-pin")
                || path.startsWith("/vault/reset-pin/")) {
            return true;
        }

        UUID builderId = TenantContext.getBuilderIdOrNull();
        if (builderId == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        HttpSession session = request.getSession(false);
        Duration maxAge = Duration.ofMinutes(unlockTimeoutMinutes);
        if (VaultSession.isUnlocked(session, builderId, maxAge)) {
            return true;
        }

        String redirectTarget = path;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            redirectTarget = redirectTarget + "?" + query;
        }
        response.sendRedirect(
                request.getContextPath()
                        + "/vault/unlock?redirect="
                        + java.net.URLEncoder.encode(redirectTarget, java.nio.charset.StandardCharsets.UTF_8));
        return false;
    }
}
