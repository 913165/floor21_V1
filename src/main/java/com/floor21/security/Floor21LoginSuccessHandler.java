package com.floor21.security;

import com.floor21.service.PlatformAdminService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Floor21LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final PlatformAdminService platformAdminService;

    {
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        if (authentication.getPrincipal() instanceof Floor21UserPrincipal principal) {
            platformAdminService.recordLogin(principal.getEmail());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
