package com.floor21.controller;

import com.floor21.entity.BuilderExpense;
import com.floor21.security.ExpensesSession;
import com.floor21.security.TenantContext;
import com.floor21.service.BuilderExpenseService;
import com.floor21.service.ExpensesPinService;
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
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpensesHubController {

    private final BuilderExpenseService builderExpenseService;
    private final ExpensesPinService expensesPinService;

    @Value("${floor21.expenses.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @InitBinder("expenseForm")
    public void expenseFormBinder(WebDataBinder binder) {
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
                            setValue(new BigDecimal(text.replace(",", "")));
                        }
                    }
                });
    }

    @GetMapping("/unlock")
    public String unlockForm(@RequestParam(required = false) String redirect, Model model) {
        model.addAttribute("pageTitle", "Expenses access");
        model.addAttribute("pinSetupRequired", !expensesPinService.hasPinConfigured());
        model.addAttribute("redirectPath", sanitizeRedirect(redirect));
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        return "expenses-hub/unlock";
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
        boolean setup = !expensesPinService.hasPinConfigured();
        try {
            if (setup) {
                if (newPin == null || confirmPin == null || !newPin.equals(confirmPin)) {
                    ra.addFlashAttribute("errorMessage", "New PIN and confirmation must match.");
                    return "redirect:/expenses/unlock?redirect=" + encodeRedirect(target);
                }
                ExpensesPinService.validatePinFormat(newPin);
                expensesPinService.setPin(newPin);
                ExpensesSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Expenses PIN created. Expenses is now unlocked.");
            } else {
                if (pin == null || pin.isBlank()) {
                    ra.addFlashAttribute("errorMessage", "Enter your expenses PIN.");
                    return "redirect:/expenses/unlock?redirect=" + encodeRedirect(target);
                }
                if (!expensesPinService.verifyPin(pin)) {
                    ra.addFlashAttribute("errorMessage", "Incorrect expenses PIN.");
                    return "redirect:/expenses/unlock?redirect=" + encodeRedirect(target);
                }
                ExpensesSession.unlock(session, TenantContext.requireBuilderId());
                ra.addFlashAttribute("successMessage", "Expenses unlocked.");
            }
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/expenses/unlock?redirect=" + encodeRedirect(target);
        }
    }

    @PostMapping("/lock")
    public String lock(HttpSession session, RedirectAttributes ra) {
        ExpensesSession.clear(session);
        ra.addFlashAttribute("successMessage", "Expenses locked.");
        return "redirect:/expenses/unlock";
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) boolean add,
            @RequestParam(required = false) UUID editId,
            Model model,
            HttpSession session) {
        model.addAttribute("pageTitle", "Expenses");
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        model.addAttribute(
                "expensesUnlocked",
                ExpensesSession.isUnlocked(
                        session, TenantContext.requireBuilderId(), Duration.ofMinutes(unlockTimeoutMinutes)));
        model.addAttribute("entries", builderExpenseService.listForTenant());
        model.addAttribute("totalAmount", builderExpenseService.totalForTenant());

        boolean showForm = add || editId != null;
        model.addAttribute("showExpenseForm", showForm);
        if (showForm) {
            BuilderExpense form =
                    editId != null ? builderExpenseService.getForTenant(editId) : builderExpenseService.newDraft();
            model.addAttribute("expenseForm", form);
            model.addAttribute("editingExpense", editId != null);
        }
        return "expenses-hub/list";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute("expenseForm") BuilderExpense expenseForm,
            Model model,
            RedirectAttributes ra) {
        try {
            boolean updating = expenseForm.getId() != null;
            builderExpenseService.save(expenseForm);
            ra.addFlashAttribute(
                    "successMessage", updating ? "Expense updated." : "Expense saved.");
            return "redirect:/expenses";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("pageTitle", "Expenses");
            model.addAttribute("entries", builderExpenseService.listForTenant());
            model.addAttribute("totalAmount", builderExpenseService.totalForTenant());
            model.addAttribute("showExpenseForm", true);
            model.addAttribute("expenseForm", expenseForm);
            model.addAttribute("editingExpense", expenseForm.getId() != null);
            return "expenses-hub/list";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        builderExpenseService.delete(id);
        ra.addFlashAttribute("successMessage", "Expense removed.");
        return "redirect:/expenses";
    }

    @GetMapping("/change-pin")
    public String changePinForm(Model model) {
        model.addAttribute("pageTitle", "Change expenses PIN");
        return "expenses-hub/change-pin";
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
            ExpensesPinService.validatePinFormat(newPin);
            expensesPinService.changePin(currentPin, newPin);
            ra.addFlashAttribute("successMessage", "Expenses PIN updated.");
            return "redirect:/expenses";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/expenses/change-pin";
        }
    }

    @GetMapping("/reset-pin")
    public String resetPinForm(@RequestParam(required = false) String redirect, Model model) {
        model.addAttribute("pageTitle", "Reset expenses PIN");
        model.addAttribute("redirectPath", sanitizeRedirect(redirect));
        model.addAttribute("pinAlreadyConfigured", expensesPinService.hasPinConfigured());
        return "expenses-hub/reset-pin";
    }

    @PostMapping("/reset-pin")
    public String resetPinSubmit(
            @RequestParam String accountPassword,
            @RequestParam String newPin,
            @RequestParam String confirmPin,
            @RequestParam(required = false) String redirect,
            HttpSession session,
            RedirectAttributes ra) {
        String target = sanitizeRedirect(redirect);
        try {
            if (!newPin.equals(confirmPin)) {
                throw new IllegalArgumentException("New PIN and confirmation must match.");
            }
            ExpensesPinService.validatePinFormat(newPin);
            expensesPinService.resetPinWithAccountPassword(accountPassword, newPin);
            ExpensesSession.unlock(session, TenantContext.requireBuilderId());
            ra.addFlashAttribute("successMessage", "Expenses PIN has been reset. Expenses is now unlocked.");
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/expenses/reset-pin?redirect=" + encodeRedirect(target);
        }
    }

    private static String sanitizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/expenses")) {
            return "/expenses";
        }
        if (redirect.startsWith("/expenses/unlock") || redirect.startsWith("/expenses/reset-pin")) {
            return "/expenses";
        }
        return redirect;
    }

    private static String encodeRedirect(String path) {
        return java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8);
    }
}
