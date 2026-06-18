package com.floor21.controller;

import com.floor21.dto.AdminBuilderRow;
import com.floor21.dto.ProjectLayoutDefaultsDto;
import com.floor21.dto.ProjectSnapshotBuildingDto;
import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.PlatformAdminService;
import com.floor21.service.PlatformAuditService;
import com.floor21.service.UserProjectAssignmentService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class AdminController {

    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final PlatformAdminService platformAdminService;
    private final PlatformAuditService auditService;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @GetMapping
    public String list(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastActivity") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) UUID projectId) {
        boolean readonlyView = !isSuperAdmin();
        String sortKey = PlatformAdminService.normalizeProjectsSort(sort);
        boolean ascending = PlatformAdminService.normalizeProjectsSortAscending(sortKey, dir);
        Boolean activeFilter = PlatformAdminService.parseProjectActiveFilter(active);
        Set<UUID> restrictToProjectIds = readonlyView ? allowedProjectIds() : null;
        Page<AdminBuilderRow> projectPage =
                platformAdminService.listBuildersPage(
                        page,
                        size,
                        sortKey,
                        ascending ? "asc" : "desc",
                        q,
                        activeFilter,
                        projectId,
                        restrictToProjectIds);
        List<Builder> projectOptions =
                builderRepository.findAllTenantsOrderByCompanyNameAsc().stream()
                        .filter(
                                b ->
                                        restrictToProjectIds == null
                                                || restrictToProjectIds.contains(b.getId()))
                        .toList();
        model.addAttribute("pageTitle", readonlyView ? "My projects" : "Projects");
        model.addAttribute("readonlyView", readonlyView);
        model.addAttribute("currentBuilderId", TenantContext.getBuilderIdOrNull());
        model.addAttribute("projects", projectOptions);
        model.addAttribute("filterProjectId", projectId);
        model.addAttribute("projectPage", projectPage);
        model.addAttribute("builders", projectPage.getContent());
        model.addAttribute("sort", sortKey);
        model.addAttribute("dir", ascending ? "asc" : "desc");
        model.addAttribute("pageSize", projectPage.getSize());
        model.addAttribute("pageSizeOptions", List.of(10, 25, 50));
        model.addAttribute("filterSearch", q != null ? q.trim() : "");
        model.addAttribute("filterActive", activeFilter == null ? "" : activeFilter.toString());
        return "admin/builders/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New project");
        model.addAttribute("readonlyView", false);
        model.addAttribute("builder", new Builder());
        return "admin/builders/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        boolean readonlyView = !isSuperAdmin();
        if (readonlyView && !canAccessProject(id)) {
            ra.addFlashAttribute("errorMessage", "Project not found.");
            return "redirect:/admin/projects";
        }
        Builder builder = builderRepository.findById(id).orElseThrow();
        if (builder.isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Project not found.");
            return "redirect:/admin/projects";
        }
        model.addAttribute("pageTitle", readonlyView ? "View project" : "Edit project");
        model.addAttribute("readonlyView", readonlyView);
        model.addAttribute("currentBuilderId", TenantContext.getBuilderIdOrNull());
        model.addAttribute("builder", builder);
        model.addAttribute("buildingCount", buildingRepository.countByBuilder_Id(id));
        model.addAttribute("partnerCount", userProjectAssignmentService.countForProject(id));
        return "admin/builders/form";
    }

    @GetMapping("/{id}/layout-defaults")
    @ResponseBody
    public ProjectLayoutDefaultsDto layoutDefaults(@PathVariable UUID id) {
        assertCanAccessProject(id);
        Builder builder = builderRepository.findById(id).orElseThrow();
        if (builder.isPlatformAdmin()) {
            throw new IllegalArgumentException("Not a tenant project.");
        }
        return new ProjectLayoutDefaultsDto(
                builder.getAddress() != null ? builder.getAddress() : "",
                builder.getCity() != null ? builder.getCity() : "");
    }

    @GetMapping("/{id}/snapshot-buildings")
    @ResponseBody
    public List<ProjectSnapshotBuildingDto> snapshotBuildings(@PathVariable UUID id) {
        assertCanAccessProject(id);
        Builder builder = builderRepository.findById(id).orElseThrow();
        if (builder.isPlatformAdmin()) {
            throw new IllegalArgumentException("Not a tenant project.");
        }
        return buildingRepository.findByBuilder_IdOrderByBuildingNameAsc(id).stream()
                .map(b -> new ProjectSnapshotBuildingDto(b.getId(), b.getBuildingName()))
                .toList();
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Builder form, Model model, RedirectAttributes ra) {
        if (form.getCompanyName() == null || form.getCompanyName().isBlank()) {
            boolean readonlyView = !isSuperAdmin();
            model.addAttribute("pageTitle", form.getId() == null ? "New project" : "Edit project");
            model.addAttribute("readonlyView", readonlyView);
            model.addAttribute("currentBuilderId", TenantContext.getBuilderIdOrNull());
            model.addAttribute("builder", form);
            if (form.getId() != null) {
                model.addAttribute("buildingCount", buildingRepository.countByBuilder_Id(form.getId()));
                model.addAttribute("partnerCount", userProjectAssignmentService.countForProject(form.getId()));
            }
            model.addAttribute("errorMessage", "Project name is required.");
            return "admin/builders/form";
        }
        Builder entity;
        if (form.getId() == null) {
            entity = new Builder();
            entity.setCreatedAt(Instant.now());
            entity.setPlatformAdmin(false);
            entity.setVaultEnabled(false);
            entity.setEmail(null);
            entity.setPasswordHash(null);
        } else {
            entity = builderRepository.findById(form.getId()).orElseThrow();
            if (entity.isPlatformAdmin()) {
                throw new IllegalArgumentException("Cannot edit the platform account from Projects.");
            }
        }
        entity.setCompanyName(form.getCompanyName().trim());
        entity.setCity(form.getCity());
        entity.setAddress(form.getAddress());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        entity.setUpdatedAt(Instant.now());
        builderRepository.save(entity);
        auditService.log(
                form.getId() == null ? "BUILDER_CREATED" : "BUILDER_UPDATED",
                "builder",
                entity.getId().toString(),
                entity.getId(),
                entity.getCompanyName());
        ra.addFlashAttribute("successMessage", "Project saved");
        return "redirect:/admin/projects";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable UUID id, Authentication authentication, RedirectAttributes ra) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            platformAdminService.deactivateBuilder(id, actor);
            ra.addFlashAttribute("successMessage", "Project deactivated.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/projects/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastActivity") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            Authentication authentication,
            RedirectAttributes ra) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            platformAdminService.deleteProject(id, actor);
            ra.addFlashAttribute("successMessage", "Project deleted.");
            return projectsListRedirect(projectId, q, active, page, size, sort, dir);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/projects/" + id + "/edit";
        }
    }

    private static String projectsListRedirect(
            UUID projectId, String q, String active, int page, int size, String sort, String dir) {
        String sortKey = PlatformAdminService.normalizeProjectsSort(sort);
        boolean ascending = PlatformAdminService.normalizeProjectsSortAscending(sortKey, dir);
        StringBuilder url = new StringBuilder("redirect:/admin/projects?");
        url.append("page=").append(Math.max(0, page));
        url.append("&size=").append(size);
        url.append("&sort=").append(sortKey);
        url.append("&dir=").append(ascending ? "asc" : "desc");
        if (projectId != null) {
            url.append("&projectId=").append(projectId);
        }
        if (q != null && !q.isBlank()) {
            url.append("&q=").append(java.net.URLEncoder.encode(q.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }
        if (active != null && !active.isBlank()) {
            url.append("&active=").append(active.trim());
        }
        return url.toString();
    }

    /** Legacy URL — use {@code /admin/buildings/new?builderId=} instead. */
    @GetMapping("/{builderId}/buildings/new")
    public String legacyNewBuilding(@PathVariable UUID builderId) {
        return "redirect:/admin/buildings/new?builderId=" + builderId;
    }

    private Set<UUID> allowedProjectIds() {
        Floor21UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return Set.of();
        }
        return userProjectAssignmentService.listProjectIdsForUser(
                principal.getStaffUserId(), principal.getBuilderId());
    }

    private boolean canAccessProject(UUID projectId) {
        Floor21UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        return userProjectAssignmentService.canUserAccessProject(
                principal.getStaffUserId(), principal.getBuilderId(), projectId);
    }

    private void assertCanAccessProject(UUID projectId) {
        if (isSuperAdmin()) {
            return;
        }
        if (!canAccessProject(projectId)) {
            throw new IllegalArgumentException("Project not found.");
        }
    }

    private static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    private static Floor21UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Floor21UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
