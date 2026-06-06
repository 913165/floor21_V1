package com.floor21.controller;

import com.floor21.dto.DashboardDto.RecentBookingRow;
import com.floor21.service.DashboardService;
import com.floor21.service.PlatformAdminService;
import com.floor21.security.Floor21UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PlatformAdminService platformAdminService;

    @GetMapping("/dashboard")
    public String dashboard(
            Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Floor21UserPrincipal p
                && p.isSuperAdmin()) {
            Page<RecentBookingRow> recentBookingPage =
                    platformAdminService.recentBookingsPage(page, size);
            model.addAttribute("pageTitle", "Platform dashboard");
            model.addAttribute("platformDashboard", platformAdminService.loadDashboard());
            model.addAttribute("recentBookingPage", recentBookingPage);
            model.addAttribute("recentBookings", recentBookingPage.getContent());
            model.addAttribute("pageSize", recentBookingPage.getSize());
            model.addAttribute("pageSizeOptions", List.of(10, 25, 50));
            return "admin/dashboard";
        }
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("dashboard", dashboardService.load());
        return "dashboard/index";
    }
}
