package com.floor21.controller;

import com.floor21.entity.BuilderExpense;
import com.floor21.service.BuilderExpenseService;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    /** Legacy PIN URLs — expenses open directly after login (no separate PIN). */
    @GetMapping({"/unlock", "/change-pin", "/reset-pin"})
    public String legacyPinRoutes() {
        return "redirect:/expenses";
    }

    @PostMapping({"/unlock", "/lock", "/change-pin", "/reset-pin"})
    public String legacyPinPosts() {
        return "redirect:/expenses";
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) boolean add,
            @RequestParam(required = false) UUID editId,
            Model model) {
        model.addAttribute("pageTitle", "Expenses");
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
}
