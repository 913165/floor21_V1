package com.floor21.controller;

import com.floor21.entity.User;
import com.floor21.service.AdminUserService;
import com.floor21.service.UserProjectAssignmentService;
import com.floor21.util.IndianStates;
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
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Users");
        model.addAttribute("users", adminUserService.listAllUsers());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String formNew(Model model) {
        return userForm(new User(), model);
    }

    @GetMapping("/{userId}/edit")
    public String formEdit(@PathVariable UUID userId, Model model) {
        return userForm(adminUserService.requireUser(userId), model);
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute User staff,
            @RequestParam(required = false) String rawPassword,
            Model model,
            RedirectAttributes ra) {
        try {
            adminUserService.savePlatformUser(staff, rawPassword);
            ra.addFlashAttribute("successMessage", "User saved.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("pageTitle", staff.getId() == null ? "New user" : "Edit user");
            model.addAttribute("staff", staff);
            model.addAttribute("indianStates", IndianStates.all());
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/users/form";
        }
    }

    private String userForm(User staff, Model model) {
        model.addAttribute("pageTitle", staff.getId() == null ? "New user" : "Edit user");
        model.addAttribute("staff", staff);
        model.addAttribute("indianStates", IndianStates.all());
        boolean assignedToProject =
                staff.getId() != null && userProjectAssignmentService.hasAnyMembership(staff.getId());
        model.addAttribute("assignedToProject", assignedToProject);
        if (assignedToProject) {
            model.addAttribute("assignedProjectName", userProjectAssignmentService.formatProjectNames(staff.getId()));
        }
        return "admin/users/form";
    }
}
