package com.floor21.controller;

import com.floor21.entity.User;
import com.floor21.service.AdminStaffService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/builders/{builderId}/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private final AdminStaffService adminStaffService;

    @GetMapping
    public String list(@PathVariable UUID builderId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        model.addAttribute("pageTitle", "Staff — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("staff", adminStaffService.listStaff(builderId));
        return "admin/staff/list";
    }

    @GetMapping("/new")
    public String formNew(@PathVariable UUID builderId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        model.addAttribute("pageTitle", "New staff — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("staff", new User());
        return "admin/staff/form";
    }

    @GetMapping("/{staffId}/edit")
    public String formEdit(@PathVariable UUID builderId, @PathVariable UUID staffId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        model.addAttribute("pageTitle", "Edit staff — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("staff", adminStaffService.getStaff(builderId, staffId));
        return "admin/staff/form";
    }

    @PostMapping("/save")
    public String save(
            @PathVariable UUID builderId,
            @ModelAttribute User staff,
            @RequestParam(required = false) String rawPassword,
            RedirectAttributes ra) {
        try {
            adminStaffService.save(builderId, staff, rawPassword);
            ra.addFlashAttribute("successMessage", "Staff saved.");
            return "redirect:/admin/builders/" + builderId + "/staff";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return staff.getId() != null
                    ? "redirect:/admin/builders/" + builderId + "/staff/" + staff.getId() + "/edit"
                    : "redirect:/admin/builders/" + builderId + "/staff/new";
        }
    }
}
