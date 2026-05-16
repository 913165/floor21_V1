package com.floor21.controller;

import com.floor21.entity.VaultEntry;
import com.floor21.security.TenantContext;
import com.floor21.security.VaultSession;
import com.floor21.service.VaultEntryService;
import com.floor21.service.VaultPinService;
import jakarta.servlet.http.HttpSession;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultEntryService vaultEntryService;
    private final VaultPinService vaultPinService;

    @Value("${floor21.vault.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @InitBinder("entry")
    public void entryBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                LocalDate.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(LocalDate.parse(text));
                        }
                    }
                });
        binder.registerCustomEditor(
                BigDecimal.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(new BigDecimal(text));
                        }
                    }
                });
    }

    @GetMapping("/unlock")
    public String unlockForm(@RequestParam(required = false) String redirect, Model model) {
        model.addAttribute("pageTitle", "Vault access");
        model.addAttribute("pinSetupRequired", !vaultPinService.hasPinConfigured());
        model.addAttribute("redirectPath", sanitizeRedirect(redirect));
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        return "vault/unlock";
    }

    @PostMapping("/unlock")
    public String unlockSubmit(
            @RequestParam(required = false) String pin,
            @RequestParam(required = false) String newPin,
            @RequestParam(required = false) String confirmPin,
            @RequestParam(required = false) String redirect,
            HttpSession session,
            RedirectAttributes ra) {
        String target = sanitizeRedirect(redirect);
        boolean setup = !vaultPinService.hasPinConfigured();
        try {
            if (setup) {
                if (newPin == null || confirmPin == null || !newPin.equals(confirmPin)) {
                    ra.addFlashAttribute("errorMessage", "New PIN and confirmation must match.");
                    return "redirect:/vault/unlock?setup=true&redirect=" + encodeRedirect(target);
                }
                VaultPinService.validatePinFormat(newPin);
                vaultPinService.setPin(newPin);
                VaultSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Vault PIN created. Vault is now unlocked.");
            } else {
                if (pin == null || pin.isBlank()) {
                    ra.addFlashAttribute("errorMessage", "Enter your vault PIN.");
                    return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
                }
                if (!vaultPinService.verifyPin(pin)) {
                    ra.addFlashAttribute("errorMessage", "Incorrect vault PIN.");
                    return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
                }
                VaultSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Vault unlocked.");
            }
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            if (setup) {
                return "redirect:/vault/unlock?setup=true&redirect=" + encodeRedirect(target);
            }
            return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
        }
    }

    @PostMapping("/lock")
    public String lock(HttpSession session, RedirectAttributes ra) {
        VaultSession.clear(session);
        ra.addFlashAttribute("successMessage", "Vault locked.");
        return "redirect:/vault/unlock";
    }

    @GetMapping
    public String list(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "Vault");
        model.addAttribute("entries", vaultEntryService.list());
        model.addAttribute("totalAmount", vaultEntryService.totalAmount());
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        model.addAttribute(
                "vaultUnlocked",
                VaultSession.isUnlocked(
                        session, TenantContext.requireBuilderId(), Duration.ofMinutes(unlockTimeoutMinutes)));
        return "vault/list";
    }

    @GetMapping("/new")
    public String formNew(Model model) {
        VaultEntry entry = new VaultEntry();
        entry.setEntryDate(LocalDate.now());
        model.addAttribute("pageTitle", "New vault entry");
        model.addAttribute("entry", entry);
        return "vault/form";
    }

    @GetMapping("/{id}/edit")
    public String formEdit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit vault entry");
        model.addAttribute("entry", vaultEntryService.get(id));
        return "vault/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute VaultEntry entry, RedirectAttributes ra) {
        try {
            vaultEntryService.save(entry);
            ra.addFlashAttribute("successMessage", "Vault entry saved.");
            return "redirect:/vault";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            if (entry.getId() != null) {
                return "redirect:/vault/" + entry.getId() + "/edit";
            }
            return "redirect:/vault/new";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        vaultEntryService.delete(id);
        ra.addFlashAttribute("successMessage", "Vault entry removed.");
        return "redirect:/vault";
    }

    @GetMapping("/change-pin")
    public String changePinForm(Model model) {
        model.addAttribute("pageTitle", "Change vault PIN");
        return "vault/change-pin";
    }

    @PostMapping("/change-pin")
    public String changePinSubmit(
            @RequestParam String currentPin,
            @RequestParam String newPin,
            @RequestParam String confirmPin,
            RedirectAttributes ra) {
        try {
            if (!newPin.equals(confirmPin)) {
                throw new IllegalArgumentException("New PIN and confirmation must match.");
            }
            VaultPinService.validatePinFormat(newPin);
            vaultPinService.changePin(currentPin, newPin);
            ra.addFlashAttribute("successMessage", "Vault PIN updated.");
            return "redirect:/vault";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vault/change-pin";
        }
    }

    private static String sanitizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/vault")) {
            return "/vault";
        }
        if (redirect.startsWith("/vault/unlock")) {
            return "/vault";
        }
        return redirect;
    }

    private static String encodeRedirect(String path) {
        return java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8);
    }
}
