package com.floor21.controller;

import com.floor21.entity.Building;
import com.floor21.entity.Slab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.service.BuildingService;
import com.floor21.service.RateSlabExcelService;
import com.floor21.service.SlabService;
import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/admin/builder-pricing-slabs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBuilderPricingSlabController {

    private final SlabService slabService;
    private final RateSlabExcelService rateSlabExcelService;
    private final BuildingService buildingService;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;

    @InitBinder("slab")
    public void slabBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                UUID.class,
                "building.id",
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(UUID.fromString(text));
                        }
                    }
                });
        binder.registerCustomEditor(
                UUID.class,
                "builder.id",
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(UUID.fromString(text));
                        }
                    }
                });
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID buildingId,
            Model model) {
        String search = q != null ? q.trim() : "";
        UUID filterBuilderId = projectId;
        UUID selectedBuilderId = null;
        if (buildingId != null) {
            Building building = buildingRepository.findByIdWithBuilder(buildingId).orElse(null);
            if (building != null) {
                model.addAttribute("selectedBuilding", building);
                if (building.getBuilder() != null) {
                    filterBuilderId = building.getBuilder().getId();
                    selectedBuilderId = building.getBuilder().getId();
                }
            }
        }
        List<Building> buildings = buildingService.filterForPlatformAdmin(projectId, search);
        model.addAttribute("pageTitle", "Milestone settings");
        model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
        model.addAttribute("filterProjectId", projectId);
        model.addAttribute("filterSearch", search);
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("selectedBuilderId", selectedBuilderId);
        model.addAttribute("buildings", buildings);
        model.addAttribute(
                "slabs",
                buildingId != null
                        ? slabService.listFilteredForPlatformAdmin(filterBuilderId, buildingId, search)
                        : Collections.emptyList());
        model.addAttribute("importReady", buildingId != null && selectedBuilderId != null);
        return "slabs/list";
    }

    @GetMapping("/new")
    public String form(
            @RequestParam UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            Model model) {
        Building building =
                buildingRepository
                        .findByIdWithBuilder(buildingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        Slab slab = new Slab();
        slab.setBuilding(building);
        if (building.getBuilder() != null) {
            slab.setBuilder(building.getBuilder());
        }
        populateFormContext(model, "New rate slab", slab, building, buildingId, projectId, q);
        return "slabs/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            Model model) {
        Slab slab = slabService.getForPlatformAdmin(id);
        UUID ctxBuildingId = buildingId;
        if (ctxBuildingId == null && slab.getBuilding() != null) {
            ctxBuildingId = slab.getBuilding().getId();
        }
        Building building = null;
        if (ctxBuildingId != null) {
            building =
                    buildingRepository
                            .findByIdWithBuilder(ctxBuildingId)
                            .orElse(null);
        }
        populateFormContext(model, "Edit rate slab", slab, building, ctxBuildingId, projectId, q);
        return "slabs/form";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute Slab slab,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            RedirectAttributes ra) {
        try {
            slabService.saveForPlatformAdmin(slab);
            ra.addFlashAttribute("successMessage", "Rate slab saved");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            if (slab.getId() == null) {
                return redirectNewForm(buildingId, projectId, q);
            }
            return redirectEditForm(slab.getId(), buildingId, projectId, q);
        }
        return redirectList(projectId, q, buildingId);
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            RedirectAttributes ra) {
        slabService.deleteForPlatformAdmin(id);
        ra.addFlashAttribute("successMessage", "Milestone deleted");
        return redirectList(projectId, q, buildingId);
    }

    private void populateFormContext(
            Model model,
            String pageTitle,
            Slab slab,
            Building building,
            UUID buildingId,
            UUID projectId,
            String q) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("slab", slab);
        model.addAttribute("selectedBuilding", building);
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("filterProjectId", projectId);
        model.addAttribute("filterSearch", q != null ? q.trim() : "");
    }

    private static String redirectNewForm(UUID buildingId, UUID projectId, String q) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromPath("/admin/builder-pricing-slabs/new");
        if (buildingId != null) {
            builder.queryParam("buildingId", buildingId);
        }
        if (projectId != null) {
            builder.queryParam("projectId", projectId);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q.trim());
        }
        return "redirect:" + builder.build().toUriString();
    }

    private static String redirectEditForm(UUID id, UUID buildingId, UUID projectId, String q) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromPath("/admin/builder-pricing-slabs/" + id + "/edit");
        if (buildingId != null) {
            builder.queryParam("buildingId", buildingId);
        }
        if (projectId != null) {
            builder.queryParam("projectId", projectId);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q.trim());
        }
        return "redirect:" + builder.build().toUriString();
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        byte[] body = rateSlabExcelService.buildImportTemplate();
        ContentDisposition disposition =
                ContentDisposition.attachment().filename("milestone_settings_sample.xlsx").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/import")
    public String importExcel(
            @RequestParam UUID builderId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replaceExisting,
            RedirectAttributes ra) {
        try {
            int imported = rateSlabExcelService.importForBuilder(builderId, buildingId, file, replaceExisting);
            ra.addFlashAttribute(
                    "successMessage",
                    "Imported " + imported + " rate slab" + (imported == 1 ? "" : "s") + " from Excel.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectList(projectId, q, buildingId);
    }

    private static String redirectList(UUID projectId, String q, UUID buildingId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/builder-pricing-slabs");
        if (projectId != null) {
            builder.queryParam("projectId", projectId);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q.trim());
        }
        if (buildingId != null) {
            builder.queryParam("buildingId", buildingId);
        }
        return "redirect:" + builder.build().toUriString();
    }
}
