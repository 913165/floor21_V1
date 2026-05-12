package com.floor21.controller;

import com.floor21.entity.ExtraExpense;
import com.floor21.service.BookingService;
import com.floor21.service.ExtraExpenseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings/{bookingId}/expenses")
@RequiredArgsConstructor
public class ExtraExpenseController {

    private final ExtraExpenseService extraExpenseService;
    private final BookingService bookingService;

    @GetMapping
    public String list(@PathVariable UUID bookingId, Model model) {
        model.addAttribute("pageTitle", "Extra Expenses");
        model.addAttribute("booking", bookingService.get(bookingId));
        model.addAttribute("expenses", extraExpenseService.list(bookingId));
        return "expenses/list";
    }

    @GetMapping("/new")
    public String form(@PathVariable UUID bookingId, Model model) {
        model.addAttribute("pageTitle", "New Expense");
        model.addAttribute("booking", bookingService.get(bookingId));
        ExtraExpense e = new ExtraExpense();
        e.setExpenseDate(java.time.LocalDate.now());
        model.addAttribute("expense", e);
        return "expenses/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable UUID bookingId, @ModelAttribute ExtraExpense expense, RedirectAttributes ra) {
        extraExpenseService.save(bookingId, expense);
        ra.addFlashAttribute("successMessage", "Expense saved");
        return "redirect:/bookings/" + bookingId + "/expenses";
    }
}
