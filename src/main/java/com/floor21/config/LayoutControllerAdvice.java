package com.floor21.config;

import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes request path and signed-in account labels for layout fragments. */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutControllerAdvice {

    private final AccountService accountService;

    @ModelAttribute("navServletPath")
    public String navServletPath(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.getServletPath();
        return path != null ? path : "";
    }

    @ModelAttribute("navAccountLabel")
    public String navAccountLabel(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        try {
            return accountService.currentDisplayName();
        } catch (Exception ignored) {
            return authentication.getName();
        }
    }

    @ModelAttribute("navAccountEmail")
    public String navAccountEmail(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        return authentication.getName();
    }

    @ModelAttribute("navAccountInitial")
    public String navAccountInitial(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        String label = navAccountLabel(authentication);
        if (label == null || label.isBlank()) {
            String email = authentication.getName();
            return email != null && !email.isBlank()
                    ? String.valueOf(Character.toUpperCase(email.charAt(0)))
                    : "?";
        }
        return String.valueOf(Character.toUpperCase(label.trim().charAt(0)));
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Floor21UserPrincipal;
    }
}
