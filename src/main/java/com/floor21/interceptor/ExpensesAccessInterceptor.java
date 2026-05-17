package com.floor21.interceptor;

import com.floor21.security.ExpensesSession;
import com.floor21.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ExpensesAccessInterceptor implements HandlerInterceptor {

    @Value("${floor21.expenses.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith("/expenses")) {
            return true;
        }
        if (path.equals("/expenses/unlock")
                || path.startsWith("/expenses/unlock/")
                || path.equals("/expenses/reset-pin")
                || path.startsWith("/expenses/reset-pin/")) {
            return true;
        }

        UUID builderId = TenantContext.getBuilderIdOrNull();
        if (builderId == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        HttpSession session = request.getSession(false);
        Duration maxAge = Duration.ofMinutes(unlockTimeoutMinutes);
        if (ExpensesSession.isUnlocked(session, builderId, maxAge)) {
            return true;
        }

        String redirectTarget = path;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            redirectTarget = redirectTarget + "?" + query;
        }
        response.sendRedirect(
                request.getContextPath()
                        + "/expenses/unlock?redirect="
                        + java.net.URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8));
        return false;
    }
}
