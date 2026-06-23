package com.floor21.controller;

import com.floor21.dto.VaultBookingAmountForm;
import com.floor21.entity.Booking;
import com.floor21.entity.VaultEntry;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.security.DocsLockerSession;
import com.floor21.security.TenantContext;
import com.floor21.security.VaultSession;
import com.floor21.service.BuildingService;
import com.floor21.service.DocsLockerPinService;
import com.floor21.service.VaultBookingProfileService;
import com.floor21.service.VaultEntryService;
import com.floor21.service.VaultPinService;
import com.floor21.vault.VaultPickerScope;
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
    private final VaultBookingProfileService vaultBookingProfileService;
    private final VaultPinService vaultPinService;
    private final DocsLockerPinService docsLockerPinService;
    private final BuildingService buildingService;
    private final BookingRepository bookingRepository;

    @Value("${floor21.vault.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @InitBinder({"paymentEntryForm", "extraEntryForm", "expenseEntryForm", "amountForm"})
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
        model.addAttribute("pageTitle", "Vault");
        model.addAttribute("redirectPath", sanitizeRedirect(redirect));
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        return "vault/unlock";
    }

    @PostMapping("/unlock")
    public String unlockSubmit(
            @RequestParam(required = false) String pin,
            @RequestParam(required = false) String redirect,
            HttpSession session,
            RedirectAttributes ra) {
        String target = sanitizeRedirect(redirect);
        try {
            if (pin == null || pin.isBlank()) {
                ra.addFlashAttribute("errorMessage", "Enter your PIN.");
                return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
            }
            if (docsLockerPinService.verifyPin(pin)) {
                DocsLockerSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Unlocked.");
                return "redirect:/docs-locker";
            }
            if (vaultPinService.verifyPin(pin)) {
                VaultSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Unlocked.");
                return "redirect:" + vaultRedirectTarget(target);
            }
            ra.addFlashAttribute("errorMessage", "Incorrect PIN.");
            return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vault/unlock?redirect=" + encodeRedirect(target);
        }
    }

    @PostMapping("/lock")
    public String lock(HttpSession session, RedirectAttributes ra) {
        VaultSession.clear(session);
        ra.addFlashAttribute("successMessage", "Locked.");
        return "redirect:/vault/unlock";
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(name = "bookingId", required = false) String bookingPicker,
            @RequestParam(required = false) boolean addExtra,
            @RequestParam(required = false) boolean addPayment,
            @RequestParam(required = false) boolean addExpense,
            @RequestParam(required = false) UUID editExtraId,
            @RequestParam(required = false) UUID editPaymentId,
            @RequestParam(required = false) UUID editExpenseId,
            Model model,
            HttpSession session) {
        model.addAttribute("pageTitle", "Vault");
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        model.addAttribute(
                "vaultUnlocked",
                VaultSession.isUnlocked(
                        session, TenantContext.requireBuilderId(), Duration.ofMinutes(unlockTimeoutMinutes)));
        addPicker(model, buildingId, bookingPicker);
        if (VaultPickerScope.isGeneralVault(bookingPicker)) {
            boolean openIncomeForm = addExtra || addPayment;
            UUID editIncomeId = editPaymentId != null ? editPaymentId : editExtraId;
            addGeneralWorkspace(model, openIncomeForm, addExpense, editIncomeId, editExpenseId);
            return "vault/list";
        }
        UUID bookingId;
        try {
            bookingId = parseBookingId(bookingPicker);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", "Invalid booking selection.");
            return "vault/list";
        }
        if (bookingId == null) {
            return "vault/list";
        }
        try {
            Booking booking =
                    bookingRepository
                            .findByIdAndBuilder_IdForSchedule(bookingId, TenantContext.requireBuilderId())
                            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            if (!bookingMatchesBuilding(booking, buildingId)) {
                model.addAttribute(
                        "errorMessage",
                        "That booking is not in the selected building. Choose a booking from the list.");
                model.addAttribute("bookingPicker", null);
                return "vault/list";
            }
            boolean openIncomeForm = addExtra || addPayment;
            boolean openExpenseForm = addExpense;
            UUID editIncomeId = editPaymentId != null ? editPaymentId : editExtraId;
            addWorkspace(
                    model,
                    buildingId,
                    bookingId,
                    openIncomeForm,
                    openExpenseForm,
                    editIncomeId,
                    editExpenseId);
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

    @PostMapping("/amounts/save")
    public String saveAmounts(
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("amountForm") VaultBookingAmountForm amountForm,
            RedirectAttributes ra) {
        try {
            vaultBookingProfileService.saveAmountForm(amountForm);
            ra.addFlashAttribute("successMessage", "Deal amounts saved.");
            return redirectToVault(amountForm.getBookingId(), buildingId);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return redirectToVault(amountForm.getBookingId(), buildingId);
        }
    }

    @PostMapping("/extra/save")
    public String saveIncome(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("extraEntryForm") VaultEntry extraEntryForm,
            Model model,
            RedirectAttributes ra) {
        return saveIncomeInternal(bookingId, buildingId, extraEntryForm, model, ra);
    }

    @PostMapping("/income/save")
    public String saveIncomeAlias(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("extraEntryForm") VaultEntry extraEntryForm,
            Model model,
            RedirectAttributes ra) {
        return saveIncomeInternal(bookingId, buildingId, extraEntryForm, model, ra);
    }

    @PostMapping("/expense/save")
    public String saveExpense(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("expenseEntryForm") VaultEntry expenseEntryForm,
            Model model,
            RedirectAttributes ra) {
        addPicker(model, buildingId, bookingId.toString());
        try {
            boolean updating = expenseEntryForm.getId() != null;
            vaultEntryService.saveExpense(bookingId, expenseEntryForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "Expense updated." : "Expense saved.");
            return redirectToVault(bookingId, buildingId);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addWorkspace(model, buildingId, bookingId, false, true, null, expenseEntryForm.getId());
            model.addAttribute("expenseEntryForm", expenseEntryForm);
            return "vault/list";
        }
    }

    @PostMapping("/general-income/save")
    public String saveGeneralIncome(
            @ModelAttribute("incomeEntryForm") VaultEntry incomeEntryForm,
            Model model,
            RedirectAttributes ra) {
        addPicker(model, null, VaultPickerScope.GENERAL_VAULT);
        try {
            boolean updating = incomeEntryForm.getId() != null;
            vaultEntryService.saveGeneralIncome(incomeEntryForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "General income updated." : "General income saved.");
            return redirectToGeneralVault();
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addGeneralWorkspace(model, true, false, incomeEntryForm.getId(), null);
            model.addAttribute("incomeEntryForm", incomeEntryForm);
            return "vault/list";
        }
    }

    @PostMapping("/general-income/{id}/delete")
    public String deleteGeneralIncome(@PathVariable UUID id, RedirectAttributes ra) {
        vaultEntryService.deleteGeneralIncome(id);
        ra.addFlashAttribute("successMessage", "General income removed.");
        return redirectToGeneralVault();
    }

    @PostMapping("/general-expense/save")
    public String saveGeneralExpense(
            @ModelAttribute("expenseEntryForm") VaultEntry expenseEntryForm,
            Model model,
            RedirectAttributes ra) {
        addPicker(model, null, VaultPickerScope.GENERAL_VAULT);
        try {
            boolean updating = expenseEntryForm.getId() != null;
            vaultEntryService.saveGeneralExpense(expenseEntryForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "General expense updated." : "General expense saved.");
            return redirectToGeneralVault();
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addGeneralWorkspace(model, false, true, null, expenseEntryForm.getId());
            model.addAttribute("expenseEntryForm", expenseEntryForm);
            return "vault/list";
        }
    }

    @PostMapping("/general-expense/{id}/delete")
    public String deleteGeneralExpense(@PathVariable UUID id, RedirectAttributes ra) {
        vaultEntryService.deleteGeneralExpense(id);
        ra.addFlashAttribute("successMessage", "General expense removed.");
        return redirectToGeneralVault();
    }

    @PostMapping("/extra/{id}/delete")
    public String deleteIncomeLegacy(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        return deleteIncome(id, bookingId, buildingId, ra);
    }

    @PostMapping("/income/{id}/delete")
    public String deleteIncome(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        vaultEntryService.deleteIncome(id, bookingId);
        ra.addFlashAttribute("successMessage", "Income entry removed.");
        return redirectToVault(bookingId, buildingId);
    }

    @PostMapping("/expense/{id}/delete")
    public String deleteExpense(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        vaultEntryService.deleteExpense(id, bookingId);
        ra.addFlashAttribute("successMessage", "Expense removed.");
        return redirectToVault(bookingId, buildingId);
    }

    @GetMapping("/pins")
    public String pinsForm(Model model) {
        model.addAttribute("pageTitle", "Change PIN");
        model.addAttribute("pin1Configured", docsLockerPinService.hasPinConfigured());
        model.addAttribute("pin2Configured", vaultPinService.hasPinConfigured());
        return "vault/pins";
    }

    @PostMapping("/pins/pin1")
    public String pinsPin1Submit(
            @RequestParam(required = false) String currentPin,
            @RequestParam String newPin,
            @RequestParam String confirmPin,
            @RequestParam(required = false) String accountPassword,
            RedirectAttributes ra) {
        try {
            if (!newPin.equals(confirmPin)) {
                throw new IllegalArgumentException("New PIN and confirmation must match.");
            }
            DocsLockerPinService.validatePinFormat(newPin);
            if (docsLockerPinService.hasPinConfigured()) {
                docsLockerPinService.changePin(currentPin, newPin);
            } else {
                docsLockerPinService.resetPinWithAccountPassword(accountPassword, newPin);
            }
            ra.addFlashAttribute("successMessage", "PIN1 updated.");
            return "redirect:/vault/pins";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vault/pins";
        }
    }

    @PostMapping("/pins/pin2")
    public String pinsPin2Submit(
            @RequestParam(required = false) String currentPin,
            @RequestParam String newPin,
            @RequestParam String confirmPin,
            @RequestParam(required = false) String accountPassword,
            RedirectAttributes ra) {
        try {
            if (!newPin.equals(confirmPin)) {
                throw new IllegalArgumentException("New PIN and confirmation must match.");
            }
            VaultPinService.validatePinFormat(newPin);
            if (vaultPinService.hasPinConfigured()) {
                vaultPinService.changePin(currentPin, newPin);
            } else {
                vaultPinService.resetPinWithAccountPassword(accountPassword, newPin);
            }
            ra.addFlashAttribute("successMessage", "PIN2 updated.");
            return "redirect:/vault/pins";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vault/pins";
        }
    }

    @GetMapping("/change-pin")
    public String changePinForm() {
        return "redirect:/vault/pins";
    }

    @PostMapping("/change-pin")
    public String changePinSubmit() {
        return "redirect:/vault/pins";
    }

    @GetMapping("/reset-pin")
    public String resetPinForm() {
        return "redirect:/vault/pins";
    }

    @PostMapping("/reset-pin")
    public String resetPinSubmit() {
        return "redirect:/vault/pins";
    }

    private String saveIncomeInternal(
            UUID bookingId,
            UUID buildingId,
            VaultEntry extraEntryForm,
            Model model,
            RedirectAttributes ra) {
        addPicker(model, buildingId, bookingId.toString());
        try {
            boolean updating = extraEntryForm.getId() != null;
            vaultEntryService.saveIncome(bookingId, extraEntryForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "Income entry updated." : "Income entry saved.");
            return redirectToVault(bookingId, buildingId);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addWorkspace(model, buildingId, bookingId, true, false, extraEntryForm.getId(), null);
            model.addAttribute("extraEntryForm", extraEntryForm);
            return "vault/list";
        }
    }

    private void addPicker(Model model, UUID buildingId, String bookingPicker) {
        model.addAttribute("buildings", buildingService.listForVault());
        model.addAttribute("selectedBuildingId", buildingId);
        UUID builderId = TenantContext.requireBuilderId();
        List<Booking> bookings =
                buildingId == null
                        ? bookingRepository.findActiveForPaymentSchedule(builderId)
                        : bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        model.addAttribute("bookings", bookings);
        model.addAttribute("bookingPicker", bookingPicker);
        model.addAttribute("generalVault", VaultPickerScope.isGeneralVault(bookingPicker));
        model.addAttribute("generalExpenses", VaultPickerScope.isGeneralVault(bookingPicker));
        model.addAttribute("selectedBookingId", parseBookingId(bookingPicker));
    }

    private void addGeneralWorkspace(
            Model model,
            boolean showIncomeForm,
            boolean showExpenseForm,
            UUID editIncomeId,
            UUID editExpenseId) {
        model.addAttribute("generalVault", true);
        model.addAttribute("generalExpenses", true);
        java.math.BigDecimal generalIncomeTotal = vaultEntryService.totalGeneralIncome();
        java.math.BigDecimal generalExpenseTotal = vaultEntryService.totalGeneralExpenses();
        model.addAttribute("incomeEntries", vaultEntryService.listGeneralIncome());
        model.addAttribute("generalIncomeTotal", generalIncomeTotal);
        model.addAttribute("expenseEntries", vaultEntryService.listGeneralExpenses());
        model.addAttribute("generalExpenseTotal", generalExpenseTotal);
        model.addAttribute("generalNetTotal", generalIncomeTotal.subtract(generalExpenseTotal));
        model.addAttribute("showPaymentForm", showIncomeForm);
        model.addAttribute("showIncomeForm", showIncomeForm);
        model.addAttribute("showExpenseForm", showExpenseForm);
        if (showIncomeForm) {
            VaultEntry incomeForm =
                    editIncomeId != null
                            ? vaultEntryService.getGeneralIncome(editIncomeId)
                            : vaultEntryService.newGeneralIncomeDraft();
            model.addAttribute("incomeEntryForm", incomeForm);
            model.addAttribute("extraEntryForm", incomeForm);
            model.addAttribute("editingPayment", editIncomeId != null);
            model.addAttribute("editingIncome", editIncomeId != null);
        }
        if (showExpenseForm) {
            VaultEntry expenseForm =
                    editExpenseId != null
                            ? vaultEntryService.getGeneralExpense(editExpenseId)
                            : vaultEntryService.newGeneralExpenseDraft();
            model.addAttribute("expenseEntryForm", expenseForm);
            model.addAttribute("editingExpense", editExpenseId != null);
        }
    }

    private void addWorkspace(
            Model model,
            UUID buildingId,
            UUID bookingId,
            boolean showIncomeForm,
            boolean showExpenseForm,
            UUID editIncomeId,
            UUID editExpenseId) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("paymentEntries", vaultEntryService.listIncomeForBooking(bookingId));
        model.addAttribute("expenseEntries", vaultEntryService.listExpensesForBooking(bookingId));
        model.addAttribute("amountSummary", vaultEntryService.summarizeAmounts(bookingId));
        model.addAttribute("amountForm", vaultBookingProfileService.getAmountForm(bookingId));
        model.addAttribute("selectedBuildingId", buildingId);

        model.addAttribute("showPaymentForm", showIncomeForm);
        model.addAttribute("showExtraForm", showIncomeForm);
        model.addAttribute("showExpenseForm", showExpenseForm);
        if (showIncomeForm) {
            VaultEntry incomeForm =
                    editIncomeId != null
                            ? vaultEntryService.getIncomeForBooking(editIncomeId, bookingId)
                            : vaultEntryService.newIncomeDraft(booking);
            model.addAttribute("paymentEntryForm", incomeForm);
            model.addAttribute("extraEntryForm", incomeForm);
            model.addAttribute("editingPayment", editIncomeId != null);
            model.addAttribute("editingExtra", editIncomeId != null);
        }
        if (showExpenseForm) {
            VaultEntry expenseForm =
                    editExpenseId != null
                            ? vaultEntryService.getExpenseForBooking(editExpenseId, bookingId)
                            : vaultEntryService.newExpenseDraft(booking);
            model.addAttribute("expenseEntryForm", expenseForm);
            model.addAttribute("editingExpense", editExpenseId != null);
        }
    }

    private static String redirectToVault(UUID bookingId, UUID buildingId) {
        StringBuilder sb = new StringBuilder("redirect:/vault?bookingId=").append(bookingId);
        if (buildingId != null) {
            sb.append("&buildingId=").append(buildingId);
        }
        return sb.toString();
    }

    private static String redirectToGeneralVault() {
        return "redirect:/vault?bookingId=" + VaultPickerScope.GENERAL_VAULT;
    }

    private static UUID parseBookingId(String bookingPicker) {
        if (bookingPicker == null || bookingPicker.isBlank() || VaultPickerScope.isGeneralVault(bookingPicker)) {
            return null;
        }
        return UUID.fromString(bookingPicker);
    }

    private static String sanitizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/vault";
        }
        if (redirect.startsWith("/vault/unlock") || redirect.startsWith("/vault/reset-pin")) {
            return "/vault";
        }
        if (redirect.startsWith("/docs-locker")) {
            return redirect;
        }
        if (redirect.startsWith("/vault")) {
            return redirect;
        }
        return "/vault";
    }

    private static String vaultRedirectTarget(String redirect) {
        if (redirect != null && redirect.startsWith("/docs-locker")) {
            return "/vault";
        }
        return sanitizeRedirect(redirect);
    }

    private static String encodeRedirect(String path) {
        return java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean bookingMatchesBuilding(Booking booking, UUID buildingId) {
        if (buildingId == null) {
            return true;
        }
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return false;
        }
        return buildingId.equals(booking.getFlat().getBuilding().getId());
    }
}
