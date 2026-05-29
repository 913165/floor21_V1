package com.floor21.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Sends browser users to the login page when authentication is required.
 * Invalid session ids (restart, deploy, or timeout) use {@code ?relogin=true}; the login page
 * distinguishes idle timeout vs restart using client-side last-activity tracking.
 */
public class Floor21AuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String SESSION_EXPIRED_HEADER = "X-Floor21-Session-Expired";

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        boolean expired = isSessionExpired(request);
        if (wantsJsonOrTurbo(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(SESSION_EXPIRED_HEADER, expired ? "true" : "false");
            return;
        }
        response.sendRedirect(buildLoginUrl(request, expired));
    }

    static boolean isSessionExpired(HttpServletRequest request) {
        return request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
    }

    static boolean wantsJsonOrTurbo(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        if (request.getHeader("Turbo-Frame") != null) {
            return true;
        }
        if (request.getHeader("Turbo-Visit") != null) {
            return true;
        }
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    static String buildLoginUrl(HttpServletRequest request, boolean expired) {
        String base = request.getContextPath() + "/login";
        return expired ? base + "?relogin=true" : base;
    }
}
