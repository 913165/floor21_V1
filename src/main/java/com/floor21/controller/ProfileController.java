package com.floor21.controller;

import com.floor21.entity.User;
import com.floor21.service.AccountService;
import com.floor21.util.IndianStates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AccountService accountService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "My profile");
        model.addAttribute("profile", accountService.currentProfile());
        return "profile/index";
    }

    @GetMapping("/edit-company")
    public String editCompanyForm(Model model) {
        model.addAttribute("pageTitle", "Company & tax details");
        model.addAttribute("userForm", accountService.companyProfileForm());
        model.addAttribute("indianStates", IndianStates.all());
        return "profile/edit-company";
    }

    @PostMapping("/edit-company")
    public String saveCompany(
            @org.springframework.web.bind.annotation.ModelAttribute("userForm") User userForm,
            RedirectAttributes ra) {
        try {
            accountService.updateCompanyProfile(userForm);
            ra.addFlashAttribute("successMessage", "Company and tax details saved.");
            return "redirect:/profile";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/profile/edit-company";
        }
    }

    @GetMapping("/change-password")
    public String changePasswordForm(Model model) {
        model.addAttribute("pageTitle", "Change password");
        return "profile/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes ra) {
        try {
            accountService.changePassword(currentPassword, newPassword, confirmPassword);
            ra.addFlashAttribute("successMessage", "Password updated successfully.");
            return "redirect:/profile";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/profile/change-password";
        }
    }
}
