package com.floor21.controller;

import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.service.PaymentSlabTemplateService;
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
@RequestMapping("/payment-milestones")
@RequiredArgsConstructor
public class PaymentMilestoneController {

    private final PaymentSlabTemplateService paymentSlabTemplateService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Payment milestones");
        model.addAttribute("templates", paymentSlabTemplateService.listForTenant());
        return "payment-milestones/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New payment milestone");
        model.addAttribute("template", new PaymentSlabTemplate());
        return "payment-milestones/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit payment milestone");
        model.addAttribute("template", paymentSlabTemplateService.get(id));
        return "payment-milestones/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute PaymentSlabTemplate template, RedirectAttributes ra) {
        paymentSlabTemplateService.save(template);
        ra.addFlashAttribute("successMessage", "Payment milestone saved");
        return "redirect:/payment-milestones";
    }
}
