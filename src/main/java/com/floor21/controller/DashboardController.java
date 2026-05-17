package com.floor21.controller;

import com.floor21.service.DashboardService;
import com.floor21.service.PlatformAdminService;
import com.floor21.security.Floor21UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PlatformAdminService platformAdminService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Floor21UserPrincipal p
                && p.isSuperAdmin()) {
            model.addAttribute("pageTitle", "Platform dashboard");
            model.addAttribute("platformDashboard", platformAdminService.loadDashboard());
            return "admin/dashboard";
        }
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("dashboard", dashboardService.load());
        return "dashboard/index";
    }
}
