package com.floor21.controller;

import com.floor21.dto.VaultEntryBatchForm;
import com.floor21.entity.Booking;
import com.floor21.entity.VaultEntry;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.security.TenantContext;
import com.floor21.security.VaultSession;
import com.floor21.service.BuildingService;
import com.floor21.service.VaultEntryService;
import com.floor21.service.VaultPinService;
import jakarta.servlet.http.HttpSession;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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
    private final BuildingService buildingService;
    private final BookingRepository bookingRepository;

    @Value("${floor21.vault.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @InitBinder({"saveForm", "extraEntryForm"})
    public void formBinder(WebDataBinder binder) {
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
    public String list(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) boolean addExtra,
            @RequestParam(required = false) UUID editExtraId,
            Model model,
            HttpSession session) {
        model.addAttribute("pageTitle", "Vault");
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        model.addAttribute(
                "vaultUnlocked",
                VaultSession.isUnlocked(
                        session, TenantContext.requireBuilderId(), Duration.ofMinutes(unlockTimeoutMinutes)));
        addPicker(model, buildingId, bookingId);
        if (bookingId == null) {
            return "vault/list";
        }
        try {
            addWorkspace(model, buildingId, bookingId, addExtra, editExtraId);
        } catch (ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "vault/list";
    }

    @GetMapping("/new")
    public String legacyNew(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId) {
        if (bookingId != null) {
            return redirectToVault(bookingId, buildingId);
        }
        return "redirect:/vault";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("saveForm") VaultEntryBatchForm saveForm,
            Model model,
            RedirectAttributes ra) {
        UUID bookingId = saveForm.getBookingId();
        addPicker(model, buildingId, bookingId);
        try {
            vaultEntryService.saveBatch(saveForm);
            ra.addFlashAttribute("successMessage", "Vault payments saved.");
            return redirectToVault(bookingId, buildingId);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            try {
                addWorkspace(model, buildingId, bookingId, false, null);
                model.addAttribute("saveForm", saveForm);
            } catch (ResourceNotFoundException e) {
                ra.addFlashAttribute("errorMessage", ex.getMessage());
                return redirectToVault(bookingId, buildingId);
            }
            return "vault/list";
        }
    }

    @PostMapping("/extra/save")
    public String saveExtra(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("extraEntryForm") VaultEntry extraEntryForm,
            Model model,
            RedirectAttributes ra) {
        addPicker(model, buildingId, bookingId);
        try {
            boolean updating = extraEntryForm.getId() != null;
            vaultEntryService.saveExtra(bookingId, extraEntryForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "Additional vault entry updated." : "Additional vault entry saved.");
            return redirectToVault(bookingId, buildingId);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addWorkspace(model, buildingId, bookingId, true, extraEntryForm.getId());
            model.addAttribute("extraEntryForm", extraEntryForm);
            model.addAttribute("showExtraForm", true);
            model.addAttribute("editingExtra", extraEntryForm.getId() != null);
            return "vault/list";
        }
    }

    @PostMapping("/extra/{id}/delete")
    public String deleteExtra(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        vaultEntryService.deleteExtra(id, bookingId);
        ra.addFlashAttribute("successMessage", "Additional vault entry removed.");
        return redirectToVault(bookingId, buildingId);
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

    private void addPicker(Model model, UUID buildingId, UUID bookingId) {
        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        UUID builderId = TenantContext.requireBuilderId();
        List<Booking> bookings =
                buildingId == null
                        ? bookingRepository.findActiveForPaymentSchedule(builderId)
                        : bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        model.addAttribute("bookings", bookings);
        model.addAttribute("selectedBookingId", bookingId);
    }

    private void addWorkspace(
            Model model, UUID buildingId, UUID bookingId, boolean addExtra, UUID editExtraId) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("slabRows", vaultEntryService.listSlabsForBooking(bookingId));
        model.addAttribute("saveForm", vaultEntryService.buildSaveForm(bookingId));
        model.addAttribute("extraEntries", vaultEntryService.listExtraForBooking(bookingId));
        model.addAttribute("bookingTotal", vaultEntryService.totalForBooking(bookingId));
        model.addAttribute("selectedBuildingId", buildingId);

        boolean showExtraForm = addExtra || editExtraId != null;
        model.addAttribute("showExtraForm", showExtraForm);
        if (showExtraForm) {
            VaultEntry extraForm =
                    editExtraId != null
                            ? vaultEntryService.getExtraForBooking(editExtraId, bookingId)
                            : vaultEntryService.newExtraDraft(booking);
            model.addAttribute("extraEntryForm", extraForm);
            model.addAttribute("editingExtra", editExtraId != null);
        }
    }

    private static String redirectToVault(UUID bookingId, UUID buildingId) {
        StringBuilder sb = new StringBuilder("redirect:/vault?bookingId=").append(bookingId);
        if (buildingId != null) {
            sb.append("&buildingId=").append(buildingId);
        }
        return sb.toString();
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
