package com.floor21.controller;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.SlabPaymentSaveRequest;
import com.floor21.dto.SlabPaymentSaveResponse;
import com.floor21.dto.SlabPaymentSlice;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.service.BookingPaymentSlabService;
import com.floor21.service.BuildingService;
import com.floor21.service.DemandDraftService;
import com.floor21.service.SlabScheduleExportService;
import com.floor21.service.SlabScheduleLedgerService;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final SlabScheduleLedgerService slabScheduleLedgerService;
    private final DemandDraftService demandDraftService;
    private final SlabScheduleExportService slabScheduleExportService;

    @GetMapping
    public String page(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            Model model) {
        model.addAttribute("pageTitle", "Slabs — payment schedule");
        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("bookings", bookingPaymentSlabService.listBookingsForSchedule(buildingId));
        model.addAttribute("selectedBookingId", bookingId);
        if (bookingId != null) {
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
                return "bookings/payment-schedule";
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
        return "bookings/payment-schedule";
    }

    @PostMapping("/materialize")
    public String materialize(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) String replace,
            RedirectAttributes ra) {
        boolean doReplace =
                replace != null && ("true".equalsIgnoreCase(replace) || "on".equalsIgnoreCase(replace));
        try {
            bookingPaymentSlabService.materializeFromTemplates(bookingId, doReplace);
            ra.addFlashAttribute("successMessage", "Payment rows created from platform milestones.");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(bookingId, buildingId);
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
    public ResponseEntity<byte[]> exportExcel(@RequestParam UUID bookingId) {
        byte[] body = slabScheduleExportService.exportExcel(bookingId);
        String filename = slabScheduleExportService.suggestedExcelFilename(bookingId);
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
    public ResponseEntity<byte[]> exportPdf(@RequestParam UUID bookingId) {
        byte[] body = slabScheduleExportService.exportPdf(bookingId);
        String filename = slabScheduleExportService.suggestedPdfFilename(bookingId);
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
        return bookingPaymentSlabService.saveSinglePayment(request);
    }

    @DeleteMapping(value = "/payments/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SlabPaymentSaveResponse deletePayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID bookingId,
            @RequestParam UUID slabId) {
        return bookingPaymentSlabService.deleteSinglePayment(bookingId, slabId, paymentId);
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute("saveForm") BookingPaymentSlabBatchForm form,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        try {
            int saved = bookingPaymentSlabService.saveLines(form);
            ra.addFlashAttribute("successMessage", "Payment schedule saved (" + saved + " rows).");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(form.getBookingId(), buildingId);
    }

    private static String redirectBack(UUID bookingId, UUID buildingId) {
        StringBuilder sb = new StringBuilder("redirect:/bookings/payment-schedule?bookingId=").append(bookingId);
        if (buildingId != null) {
            sb.append("&buildingId=").append(buildingId);
        }
        return sb.toString();
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
}
