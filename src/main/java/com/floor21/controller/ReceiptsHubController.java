package com.floor21.controller;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Receipt;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.BankService;
import com.floor21.service.BookingOwnerService;
import com.floor21.service.BuildingService;
import com.floor21.service.ReceiptExcelService;
import com.floor21.service.ReceiptPrintService;
import com.floor21.service.ReceiptService;
import com.floor21.service.ReceiptWordExportService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptsHubController {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @InitBinder("receiptForm")
    public void receiptFormBinder(WebDataBinder binder) {
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
                            setValue(new BigDecimal(text));
                        }
                    }
                });
        binder.registerCustomEditor(
                UUID.class,
                "depositBank.id",
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
                "paidByClient.id",
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

    private final BuildingService buildingService;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final ReceiptService receiptService;
    private final BookingOwnerService bookingOwnerService;
    private final BankService bankService;
    private final ReceiptPrintService receiptPrintService;
    private final ReceiptWordExportService receiptWordExportService;
    private final ReceiptExcelService receiptExcelService;

    @GetMapping
    public String entry(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID editReceiptId,
            @RequestParam(required = false, defaultValue = "false") boolean openNew,
            HttpSession session,
            Model model) {
        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, projectId, buildingId, bookingId);
        projectId = selection.projectId();
        buildingId = selection.buildingId();
        bookingId = selection.bookingId();
        boolean platformAdminView = isPlatformAdmin();
        model.addAttribute("pageTitle", "Payment Receipts");
        model.addAttribute("platformAdminView", platformAdminView);
        model.addAttribute("readonlyView", platformAdminView);
        model.addAttribute("filterProjectId", projectId);
        model.addAttribute("editingReceipt", false);

        if (buildingId == null && bookingId != null) {
            UUID builderIdForInfer =
                    platformAdminView
                            ? resolveBuilderId(buildingId, projectId)
                            : TenantContext.requireBuilderId();
            selection =
                    MilestoneNavSession.withInferredBuilding(
                            selection,
                            MilestoneNavSupport.inferBuildingId(
                                    bookingRepository, bookingId, builderIdForInfer));
            buildingId = selection.buildingId();
            bookingId = selection.bookingId();
        }

        if (platformAdminView && (editReceiptId != null || openNew)) {
            model.addAttribute("errorMessage", "Read-only for platform admin.");
            editReceiptId = null;
            openNew = false;
        }

        if (platformAdminView) {
            populatePlatformAdminPicker(model, projectId, buildingId, bookingId);
            if (bookingId != null) {
                UUID builderId = resolveBuilderId(buildingId, projectId);
                if (builderId == null) {
                    model.addAttribute("errorMessage", "Choose a project and building to load receipts.");
                    model.addAttribute("selectedBookingId", null);
                } else if (buildingId != null && !bookingBelongsToBuilding(bookingId, builderId, buildingId)) {
                    model.addAttribute("errorMessage", "That booking is not in the selected building.");
                    model.addAttribute("selectedBookingId", null);
                } else {
                    try {
                        addBookingWorkspace(model, buildingId, bookingId, builderId, true);
                        Object effectiveBuilding = model.getAttribute("selectedBuildingId");
                        if (effectiveBuilding instanceof UUID effectiveBuildingId) {
                            buildingId = effectiveBuildingId;
                        }
                    } catch (ResourceNotFoundException ex) {
                        model.addAttribute("errorMessage", ex.getMessage());
                        model.addAttribute("selectedBookingId", null);
                    }
                }
            }
            MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
            return "receipts/entry";
        }

        populateTenantPicker(model, buildingId, bookingId);
        if (bookingId == null) {
            model.addAttribute("recentReceipts", receiptService.listForTenant());
            MilestoneNavSession.remember(session, projectId, buildingId, null);
            return "receipts/entry";
        }

        UUID tenantBuilderId = TenantContext.requireBuilderId();
        try {
            addBookingWorkspace(model, buildingId, bookingId, tenantBuilderId, false);
            Object effectiveBuilding = model.getAttribute("selectedBuildingId");
            if (effectiveBuilding instanceof UUID effectiveBuildingId) {
                buildingId = effectiveBuildingId;
            }
            boolean editingReceipt = editReceiptId != null;
            model.addAttribute("editReceiptId", editReceiptId);
            Receipt receiptForm =
                    editingReceipt
                            ? receiptService.getForBooking(editReceiptId, bookingId)
                            : newReceiptDraft();
            addReceiptFormWorkspace(model, bookingId, receiptForm);
            if (editingReceipt || openNew) {
                model.addAttribute("openReceiptModal", true);
            }
        } catch (ResourceNotFoundException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("selectedBookingId", null);
            model.addAttribute("recentReceipts", receiptService.listForTenant());
        }
        MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
        return "receipts/entry";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @ModelAttribute("receiptForm") Receipt receiptForm,
            Model model,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return redirectToEntry(bookingId, buildingId, projectId);
        }
        model.addAttribute("pageTitle", "Payment Receipts");
        model.addAttribute("platformAdminView", false);
        model.addAttribute("readonlyView", false);
        model.addAttribute("filterProjectId", projectId);
        addPageTitleAndPicker(model, projectId, buildingId, bookingId, false);
        try {
            boolean updating = receiptForm.getId() != null;
            receiptService.save(bookingId, receiptForm);
            ra.addFlashAttribute("successMessage", updating ? "Receipt updated." : "Receipt saved.");
            return redirectToEntry(bookingId, buildingId, projectId);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("receiptFormValidationFailed", true);
            model.addAttribute("openReceiptModal", true);
            try {
                addBookingWorkspace(
                        model, buildingId, bookingId, TenantContext.requireBuilderId(), false);
                addReceiptFormWorkspace(model, bookingId, receiptForm);
            } catch (ResourceNotFoundException e) {
                ra.addFlashAttribute("errorMessage", ex.getMessage());
                return redirectToEntry(bookingId, buildingId, projectId);
            }
            return "receipts/entry";
        } catch (ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return redirectToEntry(bookingId, buildingId, projectId);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return redirectToEntry(bookingId, buildingId, projectId);
        }
        try {
            receiptService.delete(id, bookingId);
            ra.addFlashAttribute("successMessage", "Payment receipt deleted.");
        } catch (ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToEntry(bookingId, buildingId, projectId);
    }

    @GetMapping("/{id}/print")
    public String print(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID builderId,
            @RequestParam(defaultValue = "false") boolean allOwners,
            Model model) {
        Receipt receipt = receiptService.getForPrint(id, bookingId, builderId);
        model.addAttribute("receipt", receipt);
        model.addAttribute("booking", receipt.getBooking());
        model.addAttribute(
                "pageTitle",
                "Receipt " + (receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : id));
        receiptPrintService.addPrintAttributes(model, receipt, allOwners);
        return "receipts/print";
    }

    @GetMapping("/print-combined")
    public String printCombined(
            @RequestParam UUID bookingId,
            @RequestParam List<UUID> ids,
            @RequestParam(required = false) UUID builderId,
            @RequestParam(defaultValue = "false") boolean allOwners,
            Model model) {
        List<Receipt> receipts = receiptService.listForCombinedPrint(bookingId, ids, builderId);
        Receipt anchor = receipts.get(receipts.size() - 1);
        model.addAttribute("receipt", anchor);
        model.addAttribute("booking", anchor.getBooking());
        model.addAttribute(
                "pageTitle",
                receipts.size() > 1 ? "Combined receipt" : "Receipt");
        model.addAttribute(
                "anyDishonoured",
                receipts.stream().anyMatch(r -> r.getDishonoured() != null && r.getDishonoured()));
        ReceiptLetterView view = receiptPrintService.buildCombinedLetterView(receipts, allOwners);
        receiptPrintService.addPrintAttributes(model, view);
        model.addAttribute("combinedReceiptIds", ids);
        model.addAttribute("combinedBuilderId", builderId);
        return "receipts/print";
    }

    @GetMapping("/download-word-combined")
    public ResponseEntity<byte[]> downloadWordCombined(
            @RequestParam UUID bookingId,
            @RequestParam List<UUID> ids,
            @RequestParam(required = false) UUID builderId,
            @RequestParam(defaultValue = "false") boolean allOwners) {
        List<Receipt> receipts = receiptService.listForCombinedPrint(bookingId, ids, builderId);
        byte[] body = receiptWordExportService.generateCombined(receipts, allOwners);
        Receipt anchor = receipts.get(receipts.size() - 1);
        String filename =
                receiptWordExportService.suggestedFilename(anchor, receipts.size() > 1);
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(body);
    }

    @GetMapping("/{id}/download-word")
    public ResponseEntity<byte[]> downloadWord(
            @PathVariable UUID id,
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID builderId,
            @RequestParam(defaultValue = "false") boolean allOwners) {
        Receipt receipt = receiptService.getForPrint(id, bookingId, builderId);
        byte[] body = receiptWordExportService.generate(receipt, allOwners);
        String filename = receiptWordExportService.suggestedFilename(receipt);
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(body);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() throws IOException {
        byte[] body = receiptExcelService.buildImportTemplate();
        ContentDisposition disposition =
                ContentDisposition.attachment().filename("payment_receipts_import_sample.xlsx").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/import")
    public String importExcel(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return redirectToEntry(bookingId, buildingId, projectId);
        }
        try {
            int imported = receiptExcelService.importForBooking(bookingId, file);
            ra.addFlashAttribute(
                    "successMessage",
                    "Imported "
                            + imported
                            + " payment receipt"
                            + (imported == 1 ? "" : "s")
                            + " from Excel.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToEntry(bookingId, buildingId, projectId);
    }

    private void populatePlatformAdminPicker(
            Model model, UUID projectId, UUID buildingId, UUID bookingId) {
        model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
        UUID requestedBuildingId = buildingId;
        buildingId = buildingService.sanitizeBuildingIdForProject(buildingId, projectId);
        if (projectId == null) {
            buildingId = null;
            bookingId = null;
        } else if (requestedBuildingId != null && buildingId == null) {
            bookingId = null;
        }
        model.addAttribute("buildings", buildingService.listBuildingsForPlatformProject(projectId));
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("selectedBookingId", bookingId);
        List<Booking> bookings = listBookingsForPlatformAdmin(buildingId, projectId);
        Booking selectedForList = null;
        if (bookingId != null) {
            UUID builderId = resolveBuilderId(buildingId, projectId);
            if (builderId != null) {
                selectedForList =
                        bookingRepository
                                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                                .orElse(null);
            }
        }
        model.addAttribute("bookings", MilestoneNavSupport.ensureSelectedBooking(bookings, selectedForList));
    }

    private void populateTenantPicker(Model model, UUID buildingId, UUID bookingId) {
        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        UUID tenantBuilderId = TenantContext.requireBuilderId();
        List<Booking> bookings;
        if (buildingId == null) {
            bookings = bookingRepository.findActiveForPaymentSchedule(tenantBuilderId);
            if (!TenantContext.hasUnrestrictedBuildingAccess()) {
                bookings =
                        bookings.stream()
                                .filter(
                                        b ->
                                                b.getFlat() != null
                                                        && b.getFlat().getBuilding() != null
                                                        && TenantContext.canAccessBuilding(
                                                                b.getFlat().getBuilding().getId()))
                                .toList();
            }
        } else if (!TenantContext.canAccessBuilding(buildingId)) {
            bookings = List.of();
        } else {
            bookings =
                    bookingRepository.findActiveForPaymentScheduleByBuilding(tenantBuilderId, buildingId);
        }
        Booking selectedForList = null;
        if (bookingId != null) {
            selectedForList =
                    bookingRepository
                            .findByIdAndBuilder_IdForSchedule(bookingId, tenantBuilderId)
                            .filter(
                                    b ->
                                            b.getFlat() == null
                                                    || b.getFlat().getBuilding() == null
                                                    || TenantContext.canAccessBuilding(
                                                            b.getFlat().getBuilding().getId()))
                            .orElse(null);
        }
        model.addAttribute("bookings", MilestoneNavSupport.ensureSelectedBooking(bookings, selectedForList));
        model.addAttribute("selectedBookingId", bookingId);
    }

    private void addPageTitleAndPicker(
            Model model, UUID projectId, UUID buildingId, UUID bookingId, boolean platformAdminView) {
        if (platformAdminView) {
            populatePlatformAdminPicker(model, projectId, buildingId, bookingId);
        } else {
            populateTenantPicker(model, buildingId, bookingId);
        }
    }

    private void addBookingWorkspace(
            Model model, UUID buildingId, UUID bookingId, UUID builderId, boolean platformAdminView) {
        Booking booking;
        if (platformAdminView) {
            booking =
                    bookingRepository
                            .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            if (buildingId != null
                    && (booking.getFlat() == null
                            || booking.getFlat().getBuilding() == null
                            || !buildingId.equals(booking.getFlat().getBuilding().getId()))) {
                throw new ResourceNotFoundException("Booking not found");
            }
        } else {
            UUID tenantBuilderId = TenantContext.requireBuilderId();
            booking =
                    bookingRepository
                            .findByIdAndBuilder_IdForSchedule(bookingId, tenantBuilderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            if (booking.getFlat() != null
                    && booking.getFlat().getBuilding() != null
                    && !TenantContext.canAccessBuilding(booking.getFlat().getBuilding().getId())) {
                throw new ResourceNotFoundException("Booking not found");
            }
        }

        if (buildingId == null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null) {
            model.addAttribute("selectedBuildingId", booking.getFlat().getBuilding().getId());
        }
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("selectedBookingId", bookingId);
        model.addAttribute("scheduleBuilderId", builderId);
        model.addAttribute("bookingOwners", bookingOwnerService.ownersInOrder(booking));
        model.addAttribute("summary", receiptService.summarizeBooking(bookingId, builderId));
        List<Receipt> history = receiptService.listHistoryForBooking(bookingId, builderId);
        model.addAttribute("receiptHistory", history);
        model.addAttribute(
                "historyTotal",
                history.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getDishonoured()))
                        .map(Receipt::getAmount)
                        .reduce(ZERO, BigDecimal::add));
    }

    private void addReceiptFormWorkspace(Model model, UUID bookingId, Receipt receiptForm) {
        model.addAttribute("receiptForm", receiptForm);
        boolean editingReceipt = receiptForm.getId() != null;
        model.addAttribute("editingReceipt", editingReceipt);
        if (!editingReceipt) {
            model.addAttribute(
                    "nextReceiptNumberPreview", receiptService.previewNextReceiptNumber(bookingId));
        }
        model.addAttribute("bankAccounts", bankService.listActiveForReceipts());
        model.addAttribute(
                "latestReceiptNumber",
                receiptService.latestReceiptNumberHint(bookingId).orElse(null));
    }

    private List<Booking> listBookingsForPlatformAdmin(UUID buildingId, UUID projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            return Collections.emptyList();
        }
        if (buildingId == null) {
            return bookingRepository.findActiveForPaymentSchedule(builderId);
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

    private static Receipt newReceiptDraft() {
        Receipt r = new Receipt();
        r.setReceiptDate(LocalDate.now());
        r.setAmountConsideration(ZERO);
        r.setAmountExtraCharges(ZERO);
        r.setAmountInterestAgreement(ZERO);
        r.setAmountInterestGst(ZERO);
        r.setAmountTds(ZERO);
        r.setAmountGstComponent(ZERO);
        r.setDishonoured(false);
        return r;
    }

    private static String redirectToEntry(UUID bookingId, UUID buildingId, UUID projectId) {
        StringBuilder sb = new StringBuilder("redirect:/receipts?bookingId=").append(bookingId);
        if (projectId != null) {
            sb.append("&projectId=").append(projectId);
        }
        if (buildingId != null) {
            sb.append("&buildingId=").append(buildingId);
        }
        return sb.toString();
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
