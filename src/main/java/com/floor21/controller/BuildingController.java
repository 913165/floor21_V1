package com.floor21.controller;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.dto.FlatAddToFloorDto;
import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.ParkingFloorConfigDto;
import com.floor21.dto.ParkingGridColDto;
import com.floor21.dto.ParkingGridRowDto;
import com.floor21.dto.ParkingLayoutDto;
import com.floor21.entity.Building;
import com.floor21.entity.Flat;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.BuildingFloorPlanService;
import com.floor21.service.BuildingService;
import com.floor21.service.FlatGridExportService;
import com.floor21.service.FlatService;
import com.floor21.service.PartnerFlatAllocationService;
import com.floor21.util.ParkingFloorConfigUtil;
import com.floor21.util.ResidentialBhkTypes;
import com.floor21.util.SkippedFloorsUtil;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
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
        model.addAttribute("bookingCounts", buildingService.countBookingsPerBuilding());
        return "buildings/list";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable UUID id, Model model, RedirectAttributes ra) {
        if (!buildingService.canEditLayout(id)) {
            ra.addFlashAttribute(
                    "errorMessage",
                    "This building has bookings. Layout cannot be changed until those bookings are removed.");
            return "redirect:/buildings";
        }
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
            Building before = null;
            if (building.getId() != null) {
                buildingService.assertLayoutEditable(building.getId());
                before = buildingService.resolveForAccess(building.getId());
            }
            Building saved = buildingService.save(building);
            if (before != null) {
                flatService.regenerateLayoutIfChanged(before, saved);
            }
            ra.addFlashAttribute("successMessage", "Building layout saved");
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

    @GetMapping("/{id}/parking-layout-image/{floorNumber}")
    public ResponseEntity<Resource> parkingLayoutImage(
            @PathVariable UUID id, @PathVariable int floorNumber) {
        if (floorNumber < 1) {
            return ResponseEntity.badRequest().build();
        }
        Building building = buildingService.resolveForAccess(id);
        String webPath = ParkingFloorConfigUtil.layoutImagePath(building, floorNumber);
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

    @PostMapping("/{id}/parking-layout-image/{floorNumber}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadParkingLayoutImage(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @RequestParam("image") MultipartFile image) {
        if (floorNumber < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid floor number."));
        }
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose an image to upload."));
        }
        try {
            buildingFloorPlanService.saveParkingLayoutImage(id, floorNumber, image);
            return ResponseEntity.ok(Map.of("success", "Parking layout image updated."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not save image. Check server disk and permissions."));
        }
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
        cfg.setSkippedFloorNumbers(SkippedFloorsUtil.formatForDisplay(b.getSkippedFloorNumbers()));
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
            model.addAttribute("maxRemovableTopFloors", flatService.getMaxRemovableTopFloors(id));
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

    @PostMapping(value = "/{id}/flats/floor/{floorNumber}/details", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> updateFloorDetails(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @Valid @RequestBody FlatAdminUpdateDto body) {
        try {
            int updated = flatService.updateFloorAsPlatformAdmin(id, floorNumber, body).size();
            return ResponseEntity.ok(Map.of("ok", true, "floorNumber", floorNumber, "updatedCount", updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/flats/floor/{floorNumber}/parking-plan")
    @ResponseBody
    public ResponseEntity<?> parkingPlan(@PathVariable UUID id, @PathVariable int floorNumber) {
        try {
            return ResponseEntity.ok(flatService.getParkingPlan(id, floorNumber));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(
            value = "/{id}/flats/floor/{floorNumber}/parking-config",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> configureParkingFloor(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @Valid @RequestBody ParkingFloorConfigDto body) {
        try {
            return ResponseEntity.ok(flatService.configureParkingFloor(id, floorNumber, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(
            value = "/{id}/flats/floor/{floorNumber}/parking-layout",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> saveParkingLayout(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @Valid @RequestBody ParkingLayoutDto body) {
        try {
            return ResponseEntity.ok(flatService.saveParkingLayout(id, floorNumber, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(
            value = "/{id}/flats/floor/{floorNumber}/parking-grid-row",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> adjustParkingGridRow(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @Valid @RequestBody ParkingGridRowDto body) {
        try {
            return ResponseEntity.ok(flatService.adjustParkingGridRow(id, floorNumber, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(
            value = "/{id}/flats/floor/{floorNumber}/parking-grid-col",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> adjustParkingGridCol(
            @PathVariable UUID id,
            @PathVariable int floorNumber,
            @Valid @RequestBody ParkingGridColDto body) {
        try {
            return ResponseEntity.ok(flatService.adjustParkingGridCol(id, floorNumber, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/flats/residential-for-parking-link")
    @ResponseBody
    public ResponseEntity<?> residentialFlatsForParkingLink(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(flatService.listResidentialFlatsForParkingLink(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/parking-slots-for-link")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> parkingSlotsForLink(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(flatService.listParkingSlotsForLink(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
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

    @PostMapping(value = "/{id}/flats/add-to-floor", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> addFlatToFloor(
            @PathVariable UUID id, @Valid @RequestBody FlatAddToFloorDto body) {
        try {
            Flat flat = flatService.addFlatToFloorAsPlatformAdmin(id, body);
            return ResponseEntity.ok(
                    Map.of(
                            "ok",
                            true,
                            "id",
                            flat.getId(),
                            "flatNumber",
                            flat.getFlatNumber(),
                            "floorNumber",
                            flat.getFloorNumber(),
                            "bhkType",
                            flat.getBhkType()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/flats/remove-top-floors")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String removeTopFloors(
            @PathVariable UUID id, @RequestParam int floorsToRemove, RedirectAttributes ra) {
        try {
            int topBefore = flatService.getTopFloorNumber(id);
            int removed = flatService.removeTopFloors(id, floorsToRemove);
            int topAfter = topBefore - removed;
            ra.addFlashAttribute(
                    "successMessage",
                    removed
                            + " top floor(s) removed (floor "
                            + (topBefore - removed + 1)
                            + " through "
                            + topBefore
                            + "). Building now ends at floor "
                            + topAfter
                            + ".");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/buildings/" + id + "/flats";
    }
}
