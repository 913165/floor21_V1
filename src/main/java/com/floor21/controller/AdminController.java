package com.floor21.controller;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.service.BuildingService;
import com.floor21.service.FlatService;
import com.floor21.service.PlatformAdminService;
import com.floor21.service.PlatformAuditService;
import com.floor21.service.PlatformSettingsService;
import com.floor21.util.ResidentialBhkTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class AdminController {

    private final BuilderRepository builderRepository;
    private final BuildingService buildingService;
    private final FlatService flatService;
    private final PlatformAdminService platformAdminService;
    private final PlatformAuditService auditService;
    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Projects");
        model.addAttribute("builders", platformAdminService.listBuilders());
        return "admin/builders/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New project");
        model.addAttribute("builder", new Builder());
        return "admin/builders/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit project");
        model.addAttribute("builder", builderRepository.findById(id).orElseThrow());
        return "admin/builders/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Builder form, Model model, RedirectAttributes ra) {
        if (form.getCompanyName() == null || form.getCompanyName().isBlank()) {
            model.addAttribute("pageTitle", form.getId() == null ? "New project" : "Edit project");
            model.addAttribute("builder", form);
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

    @GetMapping("/{builderId}/buildings/new")
    public String newBuilding(@PathVariable UUID builderId, Model model) {
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Building building = new Building();
        building.setBuildingName(builder.getCompanyName());
        building.setCity(builder.getCity());
        building.setAddress(builder.getAddress());
        building.setBhkPerFloor(ResidentialBhkTypes.emptyCountMap());
        populateProjectLayoutForm(model, builderId, builder, building);
        return "buildings/form";
    }

    @PostMapping("/{builderId}/buildings/save")
    public String saveBuilding(
            @PathVariable UUID builderId,
            @ModelAttribute Building building,
            Model model,
            RedirectAttributes ra) {
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        applyProjectDetailsToBuilding(building, builder);
        try {
            assertInitialLayoutMix(building);
            Building saved = buildingService.createForBuilder(builderId, building);
            flatService.generateFlats(saved.getId(), layoutConfigFrom(saved), false);
            long flatCount = flatService.countFlatsForBuilding(saved.getId());
            ra.addFlashAttribute(
                    "successMessage",
                    "Project layout for \""
                            + saved.getBuilder().getCompanyName()
                            + "\" created with "
                            + flatCount
                            + " flats on the grid.");
            return "redirect:/buildings/" + saved.getId() + "/flats";
        } catch (IllegalArgumentException ex) {
            mergeSubmittedBhkMix(building);
            applyProjectDetailsToBuilding(building, builder);
            populateProjectLayoutForm(model, builderId, builder, building);
            model.addAttribute("errorMessage", ex.getMessage());
            return "buildings/form";
        }
    }

    private static void populateProjectLayoutForm(
            Model model, UUID builderId, Builder builder, Building building) {
        model.addAttribute("pageTitle", "Generate project layout — " + builder.getCompanyName());
        model.addAttribute("building", building);
        model.addAttribute("projectName", builder.getCompanyName());
        model.addAttribute("builderLabel", builder.getCompanyName());
        model.addAttribute("formAction", "/admin/projects/" + builderId + "/buildings/save");
        model.addAttribute("cancelHref", "/admin/projects/" + builderId + "/edit");
        model.addAttribute("generateInitialLayout", true);
    }

    /** Keeps per-floor BHK counts on validation failure when the form is re-rendered. */
    private static void mergeSubmittedBhkMix(Building building) {
        if (building.getBhkPerFloor() != null && !building.getBhkPerFloor().isEmpty()) {
            building.setBhkPerFloor(ResidentialBhkTypes.normalizeMix(building.getBhkPerFloor()));
        }
    }

    private static void applyProjectDetailsToBuilding(Building building, Builder builder) {
        building.setBuildingName(builder.getCompanyName());
        building.setCity(builder.getCity());
        building.setAddress(builder.getAddress());
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
                            + ") to generate the project layout.");
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
}
