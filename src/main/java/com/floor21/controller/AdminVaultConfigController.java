package com.floor21.controller;

import com.floor21.service.AdminVaultConfigService;
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
@RequestMapping("/admin/vault-config")
@RequiredArgsConstructor
public class AdminVaultConfigController {

    private final AdminVaultConfigService adminVaultConfigService;

    @GetMapping
    public String page(@RequestParam(required = false) UUID projectId, Model model) {
        model.addAttribute("pageTitle", "Vault config");
        model.addAttribute("config", adminVaultConfigService.load());
        model.addAttribute("tenantScope", false);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("formAction", "/admin/vault-config");
        return "admin/vault-config";
    }

    @PostMapping
    public String save(
            @RequestParam(value = AdminVaultConfigService.GRANT_PARAM, required = false)
                    List<String> grants,
            RedirectAttributes ra) {
        adminVaultConfigService.save(grants);
        ra.addFlashAttribute("successMessage", "Vault access saved.");
        return "redirect:/admin/vault-config";
    }
}
