package com.floor21.controller;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.SlabPaymentSaveRequest;
import com.floor21.dto.SlabPaymentSaveResponse;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.BookingPaymentSlabService;
import com.floor21.service.BuildingService;
import com.floor21.service.DemandDraftService;
import com.floor21.service.SlabScheduleExportService;
import com.floor21.service.SlabScheduleLedgerService;
import jakarta.servlet.http.HttpSession;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings/payment-schedule")
@RequiredArgsConstructor
public class BookingPaymentScheduleController {

    @InitBinder("saveForm")
    public void initSaveFormBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                LocalDate.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(LocalDate.parse(text));
                        }
                    }
                });
        binder.registerCustomEditor(
                BigDecimal.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(new BigDecimal(text.replace(",", "").trim()));
                        }
                    }
                });
    }

    private final BuildingService buildingService;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final SlabScheduleLedgerService slabScheduleLedgerService;
    private final DemandDraftService demandDraftService;
    private final SlabScheduleExportService slabScheduleExportService;

    @GetMapping
    public String page(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            HttpSession session,
            Model model) {
        if (projectId == null) {
            projectId = MilestoneNavSession.readProjectId(session);
        }
        if (buildingId == null) {
            buildingId = MilestoneNavSession.readBuildingId(session);
        }
        if (bookingId == null) {
            bookingId = MilestoneNavSession.readBookingId(session);
        }
        boolean platformAdminView = isPlatformAdmin();
        model.addAttribute("pageTitle", "Payment schedule (Clients)");
        model.addAttribute("platformAdminView", platformAdminView);
        model.addAttribute("readonlyView", platformAdminView);
        model.addAttribute("filterProjectId", projectId);

        if (platformAdminView) {
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            buildingId = buildingService.sanitizeBuildingIdForProject(buildingId, projectId);
            if (buildingId == null) {
                bookingId = null;
            }
            model.addAttribute("buildings", buildingService.listBuildingsForPlatformProject(projectId));
            model.addAttribute("selectedBuildingId", buildingId);
            model.addAttribute("selectedBookingId", bookingId);
            MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
            model.addAttribute("bookings", listBookingsForPlatformAdmin(buildingId, projectId));
            if (bookingId != null) {
                loadSelectedBookingForPlatformAdmin(model, projectId, buildingId, bookingId);
            }
            return "bookings/payment-schedule";
        }

        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("bookings", bookingPaymentSlabService.listBookingsForSchedule(buildingId));
        model.addAttribute("selectedBookingId", bookingId);
        MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
        if (bookingId != null) {
            loadSelectedBookingForTenant(model, buildingId, bookingId);
        }
        return "bookings/payment-schedule";
    }

    @PostMapping("/materialize")
    public String materialize(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) String replace,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return redirectBack(bookingId, buildingId, null);
        }
        boolean doReplace =
                replace != null && ("true".equalsIgnoreCase(replace) || "on".equalsIgnoreCase(replace));
        try {
            bookingPaymentSlabService.materializeFromTemplates(bookingId, doReplace);
            ra.addFlashAttribute("successMessage", "Payment rows created from platform milestones.");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(bookingId, buildingId, null);
    }

    @GetMapping("/demand-draft")
    public ResponseEntity<byte[]> demandDraft(@RequestParam UUID bookingId) {
        byte[] body = demandDraftService.generate(bookingId);
        String filename = demandDraftService.suggestedFilename(bookingId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(body);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam UUID bookingId, @RequestParam(required = false) UUID builderId) {
        byte[] body = slabScheduleExportService.exportExcel(bookingId, builderId);
        String filename = slabScheduleExportService.suggestedExcelFilename(bookingId, builderId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam UUID bookingId, @RequestParam(required = false) UUID builderId) {
        byte[] body = slabScheduleExportService.exportPdf(bookingId, builderId);
        String filename = slabScheduleExportService.suggestedPdfFilename(bookingId, builderId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @PostMapping(value = "/payments/save", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SlabPaymentSaveResponse savePayment(@RequestBody SlabPaymentSaveRequest request) {
        if (isPlatformAdmin()) {
            throw new IllegalArgumentException("Read-only for platform admin.");
        }
        return bookingPaymentSlabService.saveSinglePayment(request);
    }

    @DeleteMapping(value = "/payments/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SlabPaymentSaveResponse deletePayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID bookingId,
            @RequestParam UUID slabId) {
        if (isPlatformAdmin()) {
            throw new IllegalArgumentException("Read-only for platform admin.");
        }
        return bookingPaymentSlabService.deleteSinglePayment(bookingId, slabId, paymentId);
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute("saveForm") BookingPaymentSlabBatchForm form,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return redirectBack(form.getBookingId(), buildingId, null);
        }
        try {
            int saved = bookingPaymentSlabService.saveLines(form);
            ra.addFlashAttribute("successMessage", "Payment schedule saved (" + saved + " rows).");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(form.getBookingId(), buildingId, null);
    }

    private void loadSelectedBookingForTenant(Model model, UUID buildingId, UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        UUID effectiveBuildingId = buildingId;
        if (effectiveBuildingId == null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null) {
            effectiveBuildingId = booking.getFlat().getBuilding().getId();
        }
        if (effectiveBuildingId != null) {
            model.addAttribute("selectedBuildingId", effectiveBuildingId);
        }
        if (!bookingMatchesBuilding(booking, buildingId)) {
            model.addAttribute(
                    "errorMessage",
                    "That booking is not in the selected building. Choose a booking from the filtered list.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        model.addAttribute("selectedBooking", booking);
        var base = bookingPaymentSlabService.baseConsideration(booking);
        model.addAttribute("baseAmount", base);
        if (bookingPaymentSlabService.materializeIfEmpty(bookingId)) {
            model.addAttribute(
                    "successMessage",
                    "Payment schedule created from platform milestones for this building.");
        }
        var ledgerRows = slabScheduleLedgerService.buildLedger(bookingId);
        model.addAttribute("ledgerRows", ledgerRows);
        model.addAttribute("ledgerSummary", slabScheduleLedgerService.summarizeLedger(ledgerRows));
    }

    private void loadSelectedBookingForPlatformAdmin(
            Model model, UUID projectId, UUID buildingId, UUID bookingId) {
        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            model.addAttribute("errorMessage", "Choose a project or building to load a booking.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        if (buildingId != null && !bookingBelongsToBuilding(bookingId, builderId, buildingId)) {
            model.addAttribute("errorMessage", "That booking is not in the selected building.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        Booking booking = bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
        if (buildingId == null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null) {
            model.addAttribute("selectedBuildingId", booking.getFlat().getBuilding().getId());
        }
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("scheduleBuilderId", builderId);
        model.addAttribute("baseAmount", bookingPaymentSlabService.baseConsideration(booking));
        var ledgerRows = slabScheduleLedgerService.buildLedgerReadOnly(bookingId, builderId);
        model.addAttribute("ledgerRows", ledgerRows);
        model.addAttribute("ledgerSummary", slabScheduleLedgerService.summarizeLedger(ledgerRows));
    }

    private List<Booking> listBookingsForPlatformAdmin(UUID buildingId, UUID projectId) {
        if (buildingId == null) {
            return Collections.emptyList();
        }
        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            return Collections.emptyList();
        }
        return bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
    }

    private UUID resolveBuilderId(UUID buildingId, UUID projectId) {
        UUID tenant = TenantContext.getBuilderIdOrNull();
        if (tenant != null) {
            return tenant;
        }
        if (buildingId != null) {
            Building building = buildingRepository.findByIdWithBuilder(buildingId).orElse(null);
            if (building != null && building.getBuilder() != null) {
                return building.getBuilder().getId();
            }
        }
        return projectId;
    }

    private boolean bookingBelongsToBuilding(UUID bookingId, UUID builderId, UUID buildingId) {
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .filter(
                        b ->
                                b.getFlat() != null
                                        && b.getFlat().getBuilding() != null
                                        && buildingId.equals(b.getFlat().getBuilding().getId()))
                .isPresent();
    }

    private static String redirectBack(UUID bookingId, UUID buildingId, UUID projectId) {
        StringBuilder sb = new StringBuilder("redirect:/bookings/payment-schedule?");
        if (projectId != null) {
            sb.append("projectId=").append(projectId).append("&");
        }
        if (buildingId != null) {
            sb.append("buildingId=").append(buildingId).append("&");
        }
        if (bookingId != null) {
            sb.append("bookingId=").append(bookingId);
        }
        String url = sb.toString();
        if (url.endsWith("?") || url.endsWith("&")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static boolean bookingMatchesBuilding(Booking booking, UUID buildingId) {
        if (buildingId == null) {
            return true;
        }
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return false;
        }
        return buildingId.equals(booking.getFlat().getBuilding().getId());
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
