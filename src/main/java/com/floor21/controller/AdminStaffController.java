package com.floor21.controller;

import com.floor21.entity.Building;
import com.floor21.entity.User;
import com.floor21.service.AdminStaffService;
import com.floor21.service.AdminUserService;
import com.floor21.service.StaffBuildingAccessService;
import com.floor21.service.UserProjectAssignmentService;
import com.floor21.util.IndianStates;
import java.util.List;
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
@RequiredArgsConstructor
public class AdminStaffController {

    private final AdminStaffService adminStaffService;
    private final AdminUserService adminUserService;
    private final StaffBuildingAccessService staffBuildingAccessService;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @GetMapping("/admin/projects/{builderId}/staff")
    public String listForBuilder(@PathVariable UUID builderId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        model.addAttribute("pageTitle", "Partners — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("building", null);
        model.addAttribute("staff", adminStaffService.listStaffViews(builderId));
        return "admin/staff/list";
    }

    @GetMapping("/admin/buildings/{buildingId}/staff")
    public String listForBuilding(@PathVariable UUID buildingId, Model model) {
        var building = adminStaffService.requireTenantBuilding(buildingId);
        var builder = building.getBuilder();
        model.addAttribute("pageTitle", "Partners — " + building.getBuildingName());
        model.addAttribute("builder", builder);
        model.addAttribute("building", building);
        model.addAttribute("staff", adminStaffService.listStaffViewsForBuilding(buildingId));
        return "admin/staff/list";
    }

    @GetMapping("/admin/projects/{builderId}/staff/assign")
    public String assignForm(@PathVariable UUID builderId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        List<Building> layouts = adminStaffService.listBuilderBuildings(builderId);
        model.addAttribute("pageTitle", "Add partner — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("availableUsers", adminUserService.listUsersAvailableForProject(builderId));
        model.addAttribute("projectLayouts", layouts);
        model.addAttribute(
                "assignedLayoutIds", layouts.size() == 1 ? List.of(layouts.getFirst().getId()) : List.of());
        return "admin/staff/assign";
    }

    @PostMapping("/admin/projects/{builderId}/staff/assign")
    public String assignSave(
            @PathVariable UUID builderId,
            @RequestParam UUID userId,
            @RequestParam String role,
            @RequestParam(required = false) List<UUID> buildingIds,
            RedirectAttributes ra) {
        try {
            adminStaffService.assignToProject(builderId, userId, role, buildingIds);
            ra.addFlashAttribute("successMessage", "Partner added to project.");
            return "redirect:/admin/projects/" + builderId + "/staff";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/projects/" + builderId + "/staff/assign";
        }
    }

    @GetMapping("/admin/projects/{builderId}/staff/new")
    public String formNewBuilder(
            @PathVariable UUID builderId,
            @RequestParam(required = false) UUID buildingId,
            Model model) {
        return staffForm(builderId, buildingId, null, model);
    }

    @GetMapping("/admin/buildings/{buildingId}/staff/new")
    public String formNewBuilding(@PathVariable UUID buildingId, Model model) {
        var building = adminStaffService.requireTenantBuilding(buildingId);
        return staffForm(building.getBuilder().getId(), buildingId, null, model);
    }

    @GetMapping("/admin/projects/{builderId}/staff/{staffId}/edit")
    public String formEditBuilder(@PathVariable UUID builderId, @PathVariable UUID staffId, Model model) {
        return staffForm(builderId, null, staffId, model);
    }

    @PostMapping("/admin/projects/{builderId}/staff/save")
    public String saveBuilder(
            @PathVariable UUID builderId,
            @ModelAttribute User staff,
            @RequestParam(required = false) String rawPassword,
            @RequestParam String role,
            @RequestParam(required = false) List<UUID> buildingIds,
            RedirectAttributes ra) {
        return saveStaff(builderId, staff, rawPassword, role, buildingIds, ra);
    }

    private String staffForm(UUID builderId, UUID preselectBuildingId, UUID staffId, Model model) {
        var builder = adminStaffService.requireTenantBuilder(builderId);
        User staff = staffId != null ? adminStaffService.getStaff(builderId, staffId) : new User();
        if (staff.getId() != null) {
            staff.setRole(userProjectAssignmentService.getRole(staff.getId(), builderId));
        } else if (staff.getRole() == null) {
            staff.setRole(StaffBuildingAccessService.ROLE_EXECUTIVE);
        }
        model.addAttribute("pageTitle", (staffId != null ? "Edit" : "New") + " staff — " + builder.getCompanyName());
        model.addAttribute("builder", builder);
        model.addAttribute("staff", staff);
        model.addAttribute("buildings", adminStaffService.listBuilderBuildings(builderId));
        model.addAttribute(
                "assignedBuildingIds",
                staff.getId() != null
                        ? staffBuildingAccessService.assignedBuildingIds(staff.getId(), builderId)
                        : (preselectBuildingId != null ? List.of(preselectBuildingId) : List.of()));
        if (preselectBuildingId != null) {
            model.addAttribute(
                    "building",
                    adminStaffService.requireTenantBuilding(preselectBuildingId));
        } else {
            model.addAttribute("building", null);
        }
        model.addAttribute("indianStates", IndianStates.all());
        return "admin/staff/form";
    }

    private String saveStaff(
            UUID builderId,
            User staff,
            String rawPassword,
            String role,
            List<UUID> buildingIds,
            RedirectAttributes ra) {
        try {
            adminStaffService.save(builderId, staff, rawPassword, role, buildingIds);
            ra.addFlashAttribute("successMessage", "Staff member saved.");
            return "redirect:/admin/projects/" + builderId + "/staff";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return staff.getId() != null
                    ? "redirect:/admin/projects/" + builderId + "/staff/" + staff.getId() + "/edit"
                    : "redirect:/admin/projects/" + builderId + "/staff/new";
        }
    }
}
