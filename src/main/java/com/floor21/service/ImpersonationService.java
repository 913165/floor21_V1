package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final UserDetailsService userDetailsService;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public boolean isImpersonating(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(ImpersonationSession.ACTIVE));
    }

    @Transactional
    public void startAsPartner(UUID builderId, UUID userId, HttpServletRequest request) {
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .filter(b -> !b.isPlatformAdmin())
                        .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        if (!Boolean.TRUE.equals(builder.getActive())) {
            throw new IllegalArgumentException("Cannot open an inactive project.");
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("Partner not found."));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Cannot open as an inactive partner.");
        }
        String role = userProjectAssignmentService.getRole(userId, builderId);
        if (role == null) {
            throw new IllegalArgumentException("User is not a partner on this project.");
        }

        AuthenticationSnapshot admin = snapshotCurrentAdmin(request);
        HttpSession session = request.getSession(true);
        session.setAttribute(ImpersonationSession.ACTIVE, Boolean.TRUE);
        session.setAttribute(ImpersonationSession.ADMIN_EMAIL, admin.email());
        session.setAttribute(ImpersonationSession.BUILDER_ID, builderId.toString());
        session.setAttribute(ImpersonationSession.BUILDER_NAME, builder.getCompanyName());
        session.setAttribute(ImpersonationSession.STAFF_USER_ID, userId.toString());
        session.setAttribute(ImpersonationSession.STAFF_NAME, user.getFullName());

        switchToStaffPrincipal(builderId, user, role);
        auditService.log(
                "IMPERSONATION_START",
                "user",
                userId.toString(),
                builderId,
                "Admin "
                        + admin.email()
                        + " opened as "
                        + user.getFullName()
                        + " ("
                        + role
                        + ") on "
                        + builder.getCompanyName());
    }

    @Transactional
    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !isImpersonating(session)) {
            throw new IllegalStateException("Not impersonating a partner.");
        }
        String adminEmail = (String) session.getAttribute(ImpersonationSession.ADMIN_EMAIL);
        String builderId = (String) session.getAttribute(ImpersonationSession.BUILDER_ID);
        String staffUserId = (String) session.getAttribute(ImpersonationSession.STAFF_USER_ID);
        clearImpersonationSession(session);
        UserDetails admin = userDetailsService.loadUserByUsername(adminEmail);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(admin, admin.getPassword(), admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
        if (builderId != null && staffUserId != null) {
            auditService.log(
                    "IMPERSONATION_END",
                    "user",
                    staffUserId,
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

    public String impersonatedStaffName(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(ImpersonationSession.STAFF_NAME);
    }

    private void switchToStaffPrincipal(UUID builderId, User user, String role) {
        var delegate =
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        Floor21UserPrincipal principal =
                new Floor21UserPrincipal(
                        builderId, user.getId(), user.getEmail(), user.getPasswordHash(), false, delegate);
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
            throw new IllegalStateException("Only platform administrators can impersonate partners.");
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
        session.removeAttribute(ImpersonationSession.STAFF_USER_ID);
        session.removeAttribute(ImpersonationSession.STAFF_NAME);
        session.removeAttribute(Floor21UserPrincipal.SESSION_BUILDER_ID);
    }

    private record AuthenticationSnapshot(String email) {}
}
