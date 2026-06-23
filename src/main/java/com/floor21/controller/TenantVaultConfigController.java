package com.floor21.controller;

import com.floor21.security.TenantContext;
import com.floor21.service.AdminVaultConfigService;
import com.floor21.service.TenantVaultConfigService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/vault-access")
@RequiredArgsConstructor
public class TenantVaultConfigController {

    private final AdminVaultConfigService adminVaultConfigService;
    private final TenantVaultConfigService tenantVaultConfigService;

    @GetMapping
    public String page(Model model, RedirectAttributes ra) {
        if (!tenantVaultConfigService.canCurrentUserManageTenantVaultConfig()) {
            ra.addFlashAttribute("errorMessage", "Vault access can only be configured by a builder admin.");
            return "redirect:/dashboard";
        }
        UUID builderId = TenantContext.requireBuilderId();
        model.addAttribute("pageTitle", "Vault access");
        model.addAttribute("config", adminVaultConfigService.loadForBuilder(builderId));
        model.addAttribute("tenantScope", true);
        model.addAttribute("selectedProjectId", builderId);
        model.addAttribute("formAction", "/settings/vault-access");
        return "admin/vault-config";
    }

    @PostMapping
    public String save(
            @RequestParam(value = AdminVaultConfigService.GRANT_PARAM, required = false)
                    List<String> grants,
            RedirectAttributes ra) {
        if (!tenantVaultConfigService.canCurrentUserManageTenantVaultConfig()) {
            ra.addFlashAttribute("errorMessage", "Vault access can only be configured by a builder admin.");
            return "redirect:/dashboard";
        }
        UUID builderId = TenantContext.requireBuilderId();
        adminVaultConfigService.saveForBuilder(builderId, grants);
        ra.addFlashAttribute("successMessage", "Vault access saved.");
        return "redirect:/settings/vault-access";
    }
}
