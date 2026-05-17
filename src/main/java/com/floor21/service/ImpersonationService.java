package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.ImpersonationSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private final BuilderRepository builderRepository;
    private final UserDetailsService userDetailsService;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public boolean isImpersonating(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(ImpersonationSession.ACTIVE));
    }

    @Transactional
    public void start(UUID builderId, HttpServletRequest request) {
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .filter(b -> !b.isPlatformAdmin())
                        .orElseThrow(() -> new IllegalArgumentException("Builder not found."));
        if (!Boolean.TRUE.equals(builder.getActive())) {
            throw new IllegalArgumentException("Cannot open an inactive builder account.");
        }
        AuthenticationSnapshot admin = snapshotCurrentAdmin(request);
        HttpSession session = request.getSession(true);
        session.setAttribute(ImpersonationSession.ACTIVE, Boolean.TRUE);
        session.setAttribute(ImpersonationSession.ADMIN_EMAIL, admin.email());
        session.setAttribute(ImpersonationSession.BUILDER_ID, builderId.toString());
        session.setAttribute(ImpersonationSession.BUILDER_NAME, builder.getCompanyName());

        switchToBuilderPrincipal(builder);
        auditService.log(
                "IMPERSONATION_START",
                "builder",
                builderId.toString(),
                builderId,
                "Admin " + admin.email() + " opened tenant as builder admin");
    }

    @Transactional
    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !isImpersonating(session)) {
            throw new IllegalStateException("Not impersonating a builder.");
        }
        String adminEmail = (String) session.getAttribute(ImpersonationSession.ADMIN_EMAIL);
        String builderId = (String) session.getAttribute(ImpersonationSession.BUILDER_ID);
        clearImpersonationSession(session);
        UserDetails admin = userDetailsService.loadUserByUsername(adminEmail);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(admin, admin.getPassword(), admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
        if (builderId != null) {
            auditService.log(
                    "IMPERSONATION_END",
                    "builder",
                    builderId,
                    UUID.fromString(builderId),
                    "Admin " + adminEmail + " ended impersonation");
        }
    }

    public String impersonatedBuilderName(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(ImpersonationSession.BUILDER_NAME);
    }

    private void switchToBuilderPrincipal(Builder builder) {
        var delegate =
                new org.springframework.security.core.userdetails.User(
                        builder.getEmail(),
                        builder.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_BUILDER_ADMIN")));
        Floor21UserPrincipal principal =
                new Floor21UserPrincipal(builder.getId(), builder.getEmail(), builder.getPasswordHash(), false, delegate);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private static AuthenticationSnapshot snapshotCurrentAdmin(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            throw new IllegalStateException("You must be signed in as a platform administrator.");
        }
        if (!principal.isSuperAdmin()) {
            throw new IllegalStateException("Only platform administrators can impersonate builders.");
        }
        HttpSession existing = request.getSession(false);
        if (existing != null && Boolean.TRUE.equals(existing.getAttribute(ImpersonationSession.ACTIVE))) {
            throw new IllegalStateException("End the current impersonation session first.");
        }
        return new AuthenticationSnapshot(principal.getEmail());
    }

    private static void clearImpersonationSession(HttpSession session) {
        session.removeAttribute(ImpersonationSession.ACTIVE);
        session.removeAttribute(ImpersonationSession.ADMIN_EMAIL);
        session.removeAttribute(ImpersonationSession.BUILDER_ID);
        session.removeAttribute(ImpersonationSession.BUILDER_NAME);
        session.removeAttribute(Floor21UserPrincipal.SESSION_BUILDER_ID);
    }

    private record AuthenticationSnapshot(String email) {}
}
