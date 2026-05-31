package com.floor21.controller;

import com.floor21.service.AdminReportService;
import com.floor21.service.BuildingService;
import com.floor21.service.ImpersonationService;
import com.floor21.service.PlatformAdminService;
import com.floor21.service.PlatformAuditService;
import com.floor21.service.PlatformSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformAdminService platformAdminService;
    private final PlatformSettingsService settingsService;
    private final PlatformAuditService auditService;
    private final AdminReportService reportService;
    private final ImpersonationService impersonationService;
    private final BuildingService buildingService;

    @GetMapping("/buildings")
    public String buildings(Model model) {
        model.addAttribute("pageTitle", "All buildings");
        model.addAttribute("buildings", buildingService.listAllForPlatformAdmin());
        model.addAttribute("bookingCounts", buildingService.countBookingsPerBuilding());
        return "admin/buildings/list";
    }

    @PostMapping("/buildings/{id}/delete")
    public String deleteBuilding(
            @PathVariable UUID id, Authentication authentication, RedirectAttributes ra) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            buildingService.deleteForPlatformAdmin(id, actor);
            ra.addFlashAttribute("successMessage", "Building deleted.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/buildings";
    }

    @GetMapping("/activity")
    public String activity(Model model) {
        model.addAttribute("pageTitle", "Platform activity");
        model.addAttribute("recentBookings", platformAdminService.recentBookings());
        model.addAttribute("recentLogins", platformAdminService.recentlyLoggedInBuilders());
        model.addAttribute("recentAudit", platformAdminService.recentAudit().stream().limit(25).toList());
        return "admin/activity";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "System settings");
        model.addAttribute("settings", settingsService.all());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PlatformSettingsService.KEY_EXPENSES_DEFAULT, params.getOrDefault("default_expenses_enabled", "false"));
        values.put(PlatformSettingsService.KEY_RECEIPT_PREFIX, params.getOrDefault("default_receipt_prefix", "RCP"));
        values.put(PlatformSettingsService.KEY_SUPPORT_EMAIL, params.getOrDefault("support_email", ""));
        settingsService.saveAll(values);
        ra.addFlashAttribute("successMessage", "Settings saved.");
        return "redirect:/admin/settings";
    }

    @GetMapping("/audit-log")
    public String auditLog(Model model) {
        model.addAttribute("pageTitle", "Audit log");
        model.addAttribute("entries", auditService.recent());
        return "admin/audit-log";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("pageTitle", "Reports");
        return "admin/reports";
    }

    @GetMapping("/reports/builders.csv")
    public ResponseEntity<Resource> exportBuilders() throws IOException {
        return reportService.exportBuilders();
    }

    @GetMapping("/reports/buildings.csv")
    public ResponseEntity<Resource> exportBuildings() throws IOException {
        return reportService.exportBuildings();
    }

    @GetMapping("/reports/inventory.csv")
    public ResponseEntity<Resource> exportInventory() throws IOException {
        return reportService.exportInventory();
    }

    @PostMapping("/projects/{builderId}/impersonate")
    public String impersonate(
            @PathVariable UUID builderId, HttpServletRequest request, RedirectAttributes ra) {
        try {
            impersonationService.start(builderId, request);
            ra.addFlashAttribute("successMessage", "You are now viewing as this builder. Use End impersonation to return.");
            return "redirect:/dashboard";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/projects/" + builderId + "/edit";
        }
    }
}
