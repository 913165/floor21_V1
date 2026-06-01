package com.floor21.controller;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.service.AdminReportService;
import com.floor21.service.BuildingService;
import com.floor21.service.FlatService;
import com.floor21.service.ImpersonationService;
import com.floor21.service.PlatformAdminService;
import com.floor21.service.PlatformAuditService;
import com.floor21.service.PlatformSettingsService;
import com.floor21.util.ResidentialBhkTypes;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformAdminService platformAdminService;
    private final PlatformSettingsService settingsService;
    private final PlatformAuditService auditService;
    private final AdminReportService reportService;
    private final ImpersonationService impersonationService;
    private final BuildingService buildingService;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final FlatService flatService;

    @GetMapping("/buildings")
    public String buildings(
            Model model, @RequestParam(required = false) UUID projectId) {
        model.addAttribute("pageTitle", "All buildings");
        model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
        model.addAttribute("filterProjectId", projectId);
        List<Building> all = buildingService.listAllForPlatformAdmin();
        if (projectId != null) {
            all =
                    all.stream()
                            .filter(
                                    b ->
                                            b.getBuilder() != null
                                                    && projectId.equals(b.getBuilder().getId()))
                            .toList();
        }
        model.addAttribute("buildings", all);
        model.addAttribute("bookingCounts", buildingService.countBookingsPerBuilding());
        return "admin/buildings/list";
    }

    @GetMapping("/buildings/new")
    public String newBuilding(
            Model model, @RequestParam(required = false) UUID builderId) {
        model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
        model.addAttribute("selectedBuilderId", builderId);
        Building building = new Building();
        building.setBhkPerFloor(ResidentialBhkTypes.emptyCountMap());
        if (builderId != null) {
            Builder builder = builderRepository.findById(builderId).orElseThrow();
            if (buildingRepository.countByBuilder_Id(builderId) == 0) {
                building.setBuildingName(builder.getCompanyName());
            }
            building.setCity(builder.getCity());
            building.setAddress(builder.getAddress());
            populateAdminBuildingLayoutForm(model, builder, building);
        } else {
            populateAdminBuildingLayoutForm(model, null, building);
        }
        return "buildings/form";
    }

    @PostMapping("/buildings/save")
    public String saveBuilding(
            @RequestParam UUID builderId,
            @ModelAttribute Building building,
            Model model,
            RedirectAttributes ra) {
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        prefillAddressFromBuilder(building, builder);
        try {
            assertInitialLayoutMix(building);
            Building saved = buildingService.createForBuilder(builderId, building);
            flatService.generateFlats(saved.getId(), layoutConfigFrom(saved), false);
            long flatCount = flatService.countFlatsForBuilding(saved.getId());
            ra.addFlashAttribute(
                    "successMessage",
                    "Building \""
                            + saved.getBuildingName()
                            + "\" created with "
                            + flatCount
                            + " flats on the grid.");
            return "redirect:/buildings/" + saved.getId() + "/flats";
        } catch (IllegalArgumentException ex) {
            mergeSubmittedBhkMix(building);
            prefillAddressFromBuilder(building, builder);
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            model.addAttribute("selectedBuilderId", builderId);
            populateAdminBuildingLayoutForm(model, builder, building);
            model.addAttribute("errorMessage", ex.getMessage());
            return "buildings/form";
        }
    }

    private static void populateAdminBuildingLayoutForm(
            Model model, Builder builder, Building building) {
        String projectLabel = builder != null ? builder.getCompanyName() : null;
        model.addAttribute(
                "pageTitle",
                projectLabel != null
                        ? "Add building layout — " + projectLabel
                        : "Add building layout");
        model.addAttribute("building", building);
        model.addAttribute("projectName", projectLabel);
        model.addAttribute("builderLabel", projectLabel);
        model.addAttribute("formAction", "/admin/buildings/save");
        model.addAttribute("cancelHref", "/admin/buildings");
        model.addAttribute("generateInitialLayout", true);
        model.addAttribute("adminBuildingsFlow", true);
    }

    private static void mergeSubmittedBhkMix(Building building) {
        if (building.getBhkPerFloor() != null && !building.getBhkPerFloor().isEmpty()) {
            building.setBhkPerFloor(ResidentialBhkTypes.normalizeMix(building.getBhkPerFloor()));
        }
    }

    private static void prefillAddressFromBuilder(Building building, Builder builder) {
        if (building.getCity() == null || building.getCity().isBlank()) {
            building.setCity(builder.getCity());
        }
        if (building.getAddress() == null || building.getAddress().isBlank()) {
            building.setAddress(builder.getAddress());
        }
    }

    private static void assertInitialLayoutMix(Building form) {
        int parking = form.getParkingFloors() != null ? form.getParkingFloors() : 0;
        int residential = form.getTotalFloors() - parking;
        if (residential <= 0) {
            return;
        }
        Map<String, Integer> mix =
                form.getBhkPerFloor() != null && !form.getBhkPerFloor().isEmpty()
                        ? ResidentialBhkTypes.normalizeMix(form.getBhkPerFloor())
                        : ResidentialBhkTypes.countsFromBuilding(form);
        int mixTotal = ResidentialBhkTypes.sumCounts(mix);
        if (mixTotal != form.getFlatsPerFloor()) {
            throw new IllegalArgumentException(
                    "Unit counts per floor must add up to flats per floor (currently "
                            + mixTotal
                            + ", expected "
                            + form.getFlatsPerFloor()
                            + ") to generate the building layout.");
        }
    }

    private static BuildingConfigDto layoutConfigFrom(Building building) {
        BuildingConfigDto cfg = new BuildingConfigDto();
        cfg.setTotalFloors(building.getTotalFloors());
        cfg.setParkingFloors(building.getParkingFloors() != null ? building.getParkingFloors() : 0);
        cfg.setFlatsPerFloor(building.getFlatsPerFloor());
        Map<String, Integer> mix = ResidentialBhkTypes.countsFromBuilding(building);
        cfg.setBhkPerFloor(mix);
        cfg.setBhk1PerFloor(mix.getOrDefault("1BHK", 0));
        cfg.setBhk2PerFloor(mix.getOrDefault("2BHK", 0));
        cfg.setBhk3PerFloor(mix.getOrDefault("3BHK", 0));
        return cfg;
    }

    @PostMapping("/buildings/{id}/delete")
    public String deleteBuilding(
            @PathVariable UUID id, Authentication authentication, RedirectAttributes ra) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            buildingService.deleteForPlatformAdmin(id, actor);
            ra.addFlashAttribute("successMessage", "Building deleted.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/buildings";
    }

    @GetMapping("/activity")
    public String activity(Model model) {
        model.addAttribute("pageTitle", "Platform activity");
        model.addAttribute("recentBookings", platformAdminService.recentBookings());
        model.addAttribute("recentLogins", platformAdminService.recentlyLoggedInBuilders());
        model.addAttribute("recentAudit", platformAdminService.recentAudit().stream().limit(25).toList());
        return "admin/activity";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "System settings");
        model.addAttribute("settings", settingsService.all());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PlatformSettingsService.KEY_EXPENSES_DEFAULT, params.getOrDefault("default_expenses_enabled", "false"));
        values.put(PlatformSettingsService.KEY_RECEIPT_PREFIX, params.getOrDefault("default_receipt_prefix", "RCP"));
        values.put(PlatformSettingsService.KEY_SUPPORT_EMAIL, params.getOrDefault("support_email", ""));
        settingsService.saveAll(values);
        ra.addFlashAttribute("successMessage", "Settings saved.");
        return "redirect:/admin/settings";
    }

    @GetMapping("/audit-log")
    public String auditLog(Model model) {
        model.addAttribute("pageTitle", "Audit log");
        model.addAttribute("entries", auditService.recent());
        return "admin/audit-log";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("pageTitle", "Reports");
        return "admin/reports";
    }

    @GetMapping("/reports/builders.csv")
    public ResponseEntity<Resource> exportBuilders() throws IOException {
        return reportService.exportBuilders();
    }

    @GetMapping("/reports/buildings.csv")
    public ResponseEntity<Resource> exportBuildings() throws IOException {
        return reportService.exportBuildings();
    }

    @GetMapping("/reports/inventory.csv")
    public ResponseEntity<Resource> exportInventory() throws IOException {
        return reportService.exportInventory();
    }

    @PostMapping("/projects/{builderId}/impersonate")
    public String impersonate(
            @PathVariable UUID builderId, HttpServletRequest request, RedirectAttributes ra) {
        try {
            impersonationService.start(builderId, request);
            ra.addFlashAttribute("successMessage", "You are now viewing as this builder. Use End impersonation to return.");
            return "redirect:/dashboard";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/projects/" + builderId + "/edit";
        }
    }
}
