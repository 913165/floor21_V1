package com.floor21.controller;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.entity.Building;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.BuildingFloorPlanService;
import com.floor21.service.BuildingService;
import com.floor21.service.FlatGridExportService;
import com.floor21.service.FlatService;
import com.floor21.service.PartnerFlatAllocationService;
import com.floor21.util.ResidentialBhkTypes;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;
    private final BuildingFloorPlanService buildingFloorPlanService;
    private final FlatService flatService;
    private final FlatGridExportService flatGridExportService;
    private final PartnerFlatAllocationService partnerFlatAllocationService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Buildings");
        if (isPlatformAdmin()) {
            model.addAttribute("platformAdminView", true);
            model.addAttribute("buildings", buildingService.listAllForPlatformAdmin());
        } else {
            model.addAttribute("platformAdminView", false);
            model.addAttribute("buildings", buildingService.listForTenant());
        }
        return "buildings/list";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        Building building = buildingService.resolveForAccess(id);
        building.setBhkPerFloor(ResidentialBhkTypes.countsFromBuilding(building));
        model.addAttribute("pageTitle", "Edit Building");
        model.addAttribute("building", building);
        if (isPlatformAdmin() && building.getBuilder() != null) {
            model.addAttribute(
                    "builderLabel",
                    building.getBuilder().getCompanyName() + " (" + building.getBuilder().getEmail() + ")");
        }
        model.addAttribute("platformAdminView", isPlatformAdmin());
        return "buildings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Building building, RedirectAttributes ra) {
        try {
            Building saved = buildingService.save(building);
            ra.addFlashAttribute("successMessage", "Building saved");
            return "redirect:/buildings/" + saved.getId() + "/flats";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return building.getId() != null
                    ? "redirect:/buildings/" + building.getId() + "/edit"
                    : "redirect:/buildings";
        }
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal p
                && p.isSuperAdmin();
    }

    private static boolean canManagePartnerAllocation() {
        return isPlatformAdmin();
    }

    /**
     * Serves uploaded floor plan bytes for the current tenant. Uses an explicit URL so Thymeleaf and browsers
     * always resolve paths correctly with {@code server.servlet.context-path}.
     */
    @GetMapping("/{id}/floor-plan/{slot}")
    public ResponseEntity<Resource> floorPlanImage(
            @PathVariable UUID id,
            @PathVariable String slot,
            @RequestParam(required = false) UUID flatId) {
        if (flatId != null) {
            UUID assignedPartnerId =
                    partnerFlatAllocationService.getAssignedPartnerIdForFlat(flatId);
            if (!partnerFlatAllocationService.isBookableByCurrentUser(id, assignedPartnerId)) {
                return ResponseEntity.status(403).build();
            }
        }
        Building b = buildingService.resolveForAccess(id);
        String key = slot.toLowerCase(Locale.ROOT);
        String webPath =
                switch (key) {
                    case "1bhk" -> b.getFloorPlan1Bhk();
                    case "2bhk" -> b.getFloorPlan2Bhk();
                    case "3bhk" -> b.getFloorPlan3Bhk();
                    default -> null;
                };
        if (webPath == null || webPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return buildingFloorPlanService
                .loadAsResource(webPath)
                .map(
                        resource -> {
                            MediaType contentType =
                                    MediaTypeFactory.getMediaType(resource.getFilename())
                                            .orElse(MediaType.APPLICATION_OCTET_STREAM);
                            return ResponseEntity.ok()
                                    .cacheControl(CacheControl.noCache())
                                    .contentType(contentType)
                                    .body(resource);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/flats")
    public String flatGrid(
            @PathVariable UUID id, @RequestParam(required = false) UUID focusFlat, Model model) {
        Building b = buildingService.resolveForAccess(id);
        BuildingConfigDto cfg = new BuildingConfigDto();
        cfg.setTotalFloors(b.getTotalFloors());
        cfg.setParkingFloors(b.getParkingFloors() != null ? b.getParkingFloors() : 0);
        cfg.setFlatsPerFloor(b.getFlatsPerFloor());
        cfg.setBhk1PerFloor(b.getBhk1PerFloor() != null ? b.getBhk1PerFloor() : 0);
        cfg.setBhk2PerFloor(b.getBhk2PerFloor() != null ? b.getBhk2PerFloor() : 0);
        cfg.setBhk3PerFloor(b.getBhk3PerFloor() != null ? b.getBhk3PerFloor() : 0);
        cfg.setBhkPerFloor(ResidentialBhkTypes.countsFromBuilding(b));
        long flatCount = flatService.countFlatsForBuilding(id);
        long activeBookings = flatService.countActiveBookingsForBuilding(id);
        model.addAttribute("pageTitle", "Flat Grid — " + b.getBuildingName());
        model.addAttribute("building", b);
        model.addAttribute("floors", flatService.getGridData(id));
        model.addAttribute("config", cfg);
        model.addAttribute("focusFlatId", focusFlat);
        model.addAttribute("flatCount", flatCount);
        model.addAttribute("activeBookingCount", activeBookings);
        model.addAttribute("platformAdminView", isPlatformAdmin());
        model.addAttribute("partnerAllocationActive", partnerFlatAllocationService.isAllocationActive(id));
        model.addAttribute(
                "partnerAllocationPartners",
                canManagePartnerAllocation() ? partnerFlatAllocationService.listPartnersForBuilding(id) : List.of());
        if (flatCount > 0) {
            model.addAttribute("topFloorNumber", flatService.getTopFloorNumber(id));
            model.addAttribute("addFloorMix", flatService.bhkMixForTopResidentialFloor(id));
        }
        return "buildings/flat-grid";
    }

    @GetMapping("/{id}/flats/export/excel")
    public ResponseEntity<byte[]> exportFlatGridExcel(@PathVariable UUID id) {
        byte[] body = flatGridExportService.exportExcel(id);
        String filename = flatGridExportService.suggestedExcelFilename(id);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/{id}/flats/export/pdf")
    public ResponseEntity<byte[]> exportFlatGridPdf(@PathVariable UUID id) {
        byte[] body = flatGridExportService.exportPdf(id);
        String filename = flatGridExportService.suggestedPdfFilename(id);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @GetMapping("/{id}/flats/export/pdf-grid")
    public ResponseEntity<byte[]> exportFlatGridVisualPdf(@PathVariable UUID id) {
        byte[] body = flatGridExportService.exportVisualGridPdf(id);
        String filename = flatGridExportService.suggestedVisualGridPdfFilename(id);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @GetMapping("/{id}/sales-partners")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public List<Map<String, Object>> salesPartners(@PathVariable UUID id) {
        return partnerFlatAllocationService.listPartnersForBuilding(id).stream()
                .map(u -> Map.<String, Object>of("id", u.getId(), "fullName", u.getFullName()))
                .toList();
    }

    @PostMapping("/{id}/partner-flats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String savePartnerFlats(
            @PathVariable UUID id, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        try {
            partnerFlatAllocationService.saveAllocations(id, params);
            ra.addFlashAttribute("successMessage", "Partner flat allocation saved.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/buildings/" + id + "/flats";
    }

    @GetMapping("/{id}/flats/data")
    @ResponseBody
    public Object flatData(@PathVariable UUID id) {
        return flatService.getGridData(id);
    }

    @PostMapping("/{id}/flats/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String generate(
            @PathVariable UUID id,
            @Valid @ModelAttribute("config") BuildingConfigDto config,
            BindingResult br,
            @RequestParam(defaultValue = "false") boolean confirmReplace,
            RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Invalid configuration");
            return "redirect:/buildings/" + id + "/flats";
        }
        try {
            flatService.generateFlats(id, config, confirmReplace);
            ra.addFlashAttribute("successMessage", "Flats generated");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/buildings/" + id + "/flats";
    }

    @PostMapping("/{id}/flats/add-floors")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String addFloorsOnTop(
            @PathVariable UUID id,
            @RequestParam int additionalFloors,
            @Valid @ModelAttribute("config") BuildingConfigDto config,
            BindingResult br,
            RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Invalid floor layout settings.");
            return "redirect:/buildings/" + id + "/flats";
        }
        try {
            int topBefore = flatService.getTopFloorNumber(id);
            int added = flatService.addFloorsOnTop(id, additionalFloors, config);
            ra.addFlashAttribute(
                    "successMessage",
                    added
                            + " residential floor(s) added above floor "
                            + topBefore
                            + " (now "
                            + (topBefore + added)
                            + " total). Existing flats were kept.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/buildings/" + id + "/flats";
    }

    @PostMapping("/{id}/floor-plans")
    public String uploadFloorPlans(
            @PathVariable UUID id,
            @RequestParam(value = "plan1Bhk", required = false) MultipartFile plan1Bhk,
            @RequestParam(value = "plan2Bhk", required = false) MultipartFile plan2Bhk,
            @RequestParam(value = "plan3Bhk", required = false) MultipartFile plan3Bhk,
            RedirectAttributes ra) {
        boolean any =
                (plan1Bhk != null && !plan1Bhk.isEmpty())
                        || (plan2Bhk != null && !plan2Bhk.isEmpty())
                        || (plan3Bhk != null && !plan3Bhk.isEmpty());
        if (!any) {
            ra.addFlashAttribute("errorMessage", "Choose at least one image to upload.");
            return "redirect:/buildings/" + id + "/flats";
        }
        try {
            buildingFloorPlanService.savePlans(id, plan1Bhk, plan2Bhk, plan3Bhk);
            ra.addFlashAttribute("successMessage", "Floor plan images updated.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", "Could not save floor plan files. Check server disk and permissions.");
        }
        return "redirect:/buildings/" + id + "/flats";
    }
}
