package com.floor21.controller;

import com.floor21.entity.User;
import com.floor21.service.AdminStaffService;
import com.floor21.service.AdminUserService;
import com.floor21.util.IndianStates;
import com.floor21.service.StaffBuildingAccessService;
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
    private final AdminStaffService adminStaffService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Users");
        model.addAttribute("users", adminUserService.listAllUsers());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String formNew(@RequestParam(required = false) UUID builderId, Model model) {
        return userForm(null, builderId, model);
    }

    @GetMapping("/{userId}/edit")
    public String formEdit(@PathVariable UUID userId, Model model) {
        User user = adminUserService.requireUser(userId);
        return userForm(user, user.getBuilder().getId(), model);
    }

    @PostMapping("/save")
    public String save(
            @RequestParam UUID builderId,
            @ModelAttribute User staff,
            @RequestParam(required = false) String rawPassword,
            @RequestParam String role,
            RedirectAttributes ra) {
        try {
            adminStaffService.save(builderId, staff, rawPassword, role, null);
            ra.addFlashAttribute("successMessage", "User saved.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            if (staff.getId() != null) {
                return "redirect:/admin/users/" + staff.getId() + "/edit";
            }
            return "redirect:/admin/users/new" + (builderId != null ? "?builderId=" + builderId : "");
        }
    }

    private String userForm(User staff, UUID builderId, Model model) {
        boolean editing = staff != null && staff.getId() != null;
        var builders = adminUserService.listTenantBuilders();
        if (builders.isEmpty()) {
            model.addAttribute("pageTitle", editing ? "Edit user" : "New user");
            model.addAttribute("noBuilders", true);
            model.addAttribute("indianStates", IndianStates.all());
            return "admin/users/form";
        }
        UUID resolvedBuilderId =
                builderId != null
                        ? builderId
                        : (editing ? staff.getBuilder().getId() : builders.getFirst().getId());
        var builder = adminStaffService.requireTenantBuilder(resolvedBuilderId);
        User formUser = editing ? staff : new User();
        if (!editing && formUser.getRole() == null) {
            formUser.setRole(StaffBuildingAccessService.ROLE_EXECUTIVE);
        }
        model.addAttribute("pageTitle", editing ? "Edit user" : "New user");
        model.addAttribute("builder", builder);
        model.addAttribute("builders", builders);
        model.addAttribute("staff", formUser);
        model.addAttribute("selectedBuilderId", resolvedBuilderId);
        model.addAttribute("indianStates", IndianStates.all());
        return "admin/users/form";
    }
}
