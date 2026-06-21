package com.floor21.controller;

import com.floor21.dto.PlatformUserView;
import com.floor21.entity.User;
import com.floor21.service.AdminUserService;
import com.floor21.service.PlatformAdminService;
import com.floor21.service.UserProjectAssignmentService;
import com.floor21.util.IndianStates;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
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
    public String list(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "companyName") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) UUID projectId) {
        String sortKey = AdminUserService.normalizeUsersSort(sort);
        boolean ascending = AdminUserService.normalizeUsersSortAscending(sortKey, dir);
        Boolean activeFilter = PlatformAdminService.parseProjectActiveFilter(active);
        Page<PlatformUserView> userPage =
                adminUserService.listUsersPage(
                        page, size, sortKey, ascending ? "asc" : "desc", q, activeFilter, projectId);
        model.addAttribute("pageTitle", "Users");
        model.addAttribute("userPage", userPage);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("projects", adminUserService.listTenantBuilders());
        model.addAttribute("sort", sortKey);
        model.addAttribute("dir", ascending ? "asc" : "desc");
        model.addAttribute("pageSize", userPage.getSize());
        model.addAttribute("pageSizeOptions", List.of(10, 25, 50));
        model.addAttribute("filterSearch", q != null ? q.trim() : "");
        model.addAttribute("filterActive", activeFilter == null ? "" : activeFilter.toString());
        model.addAttribute("filterProjectId", projectId);
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

    @PostMapping("/{userId}/delete")
    public String delete(
            @PathVariable UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "companyName") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Authentication authentication,
            RedirectAttributes ra) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            adminUserService.deleteUnassignedUser(userId, actor);
            ra.addFlashAttribute("successMessage", "User deleted.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            ra.addFlashAttribute(
                    "errorMessage",
                    "Could not delete this user because related records still exist.");
        }
        return usersListRedirect(projectId, q, active, page, size, sort, dir);
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
        } else if (staff.getId() != null) {
            model.addAttribute("deletableUser", adminUserService.canSuperAdminDeleteUser(staff.getId()));
        }
        return "admin/users/form";
    }

    private static String usersListRedirect(
            UUID projectId, String q, String active, int page, int size, String sort, String dir) {
        String sortKey = AdminUserService.normalizeUsersSort(sort);
        boolean ascending = AdminUserService.normalizeUsersSortAscending(sortKey, dir);
        StringBuilder url = new StringBuilder("redirect:/admin/users?");
        url.append("page=").append(Math.max(0, page));
        url.append("&size=").append(size);
        url.append("&sort=").append(sortKey);
        url.append("&dir=").append(ascending ? "asc" : "desc");
        if (q != null && !q.isBlank()) {
            url.append("&q=").append(java.net.URLEncoder.encode(q.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }
        if (active != null && !active.isBlank()) {
            url.append("&active=").append(active.trim());
        }
        if (projectId != null) {
            url.append("&projectId=").append(projectId);
        }
        return url.toString();
    }
}
