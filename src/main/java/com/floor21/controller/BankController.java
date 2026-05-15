package com.floor21.controller;

import com.floor21.entity.Bank;
import com.floor21.service.BankService;
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
@RequestMapping("/bank-accounts")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Bank accounts");
        model.addAttribute("banks", bankService.list());
        return "banks/list";
    }

    @GetMapping("/new")
    public String formNew(Model model) {
        model.addAttribute("pageTitle", "New bank account");
        model.addAttribute("bank", new Bank());
        return "banks/form";
    }

    @GetMapping("/{id}/edit")
    public String formEdit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit bank account");
        model.addAttribute("bank", bankService.get(id));
        return "banks/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Bank bank, RedirectAttributes ra) {
        bankService.save(bank);
        ra.addFlashAttribute("successMessage", "Bank account saved");
        return "redirect:/bank-accounts";
    }
}
