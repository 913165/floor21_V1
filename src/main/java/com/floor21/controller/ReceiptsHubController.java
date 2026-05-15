package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.entity.Receipt;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.security.TenantContext;
import com.floor21.service.BankService;
import com.floor21.service.BuildingService;
import com.floor21.service.ReceiptPrintService;
import com.floor21.service.ReceiptService;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
    }

    private final BuildingService buildingService;
    private final BookingRepository bookingRepository;
    private final ReceiptService receiptService;
    private final BankService bankService;
    private final ReceiptPrintService receiptPrintService;

    @GetMapping
    public String entry(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID editReceiptId,
            Model model) {
        addPageTitleAndPicker(model, buildingId, bookingId);
        if (bookingId == null) {
            return "receipts/entry";
        }
        Receipt receiptForm =
                editReceiptId != null
                        ? receiptService.getForBooking(editReceiptId, bookingId)
                        : newReceiptDraft();
        addReceiptWorkspace(model, buildingId, bookingId, receiptForm);
        return "receipts/entry";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @ModelAttribute("receiptForm") Receipt receiptForm,
            Model model,
            RedirectAttributes ra) {
        addPageTitleAndPicker(model, buildingId, bookingId);
        try {
            boolean updating = receiptForm.getId() != null;
            receiptService.save(bookingId, receiptForm);
            ra.addFlashAttribute("successMessage", updating ? "Receipt updated." : "Receipt saved.");
            return redirectToEntry(bookingId, buildingId);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("receiptFormValidationFailed", true);
            try {
                addReceiptWorkspace(model, buildingId, bookingId, receiptForm);
            } catch (ResourceNotFoundException e) {
                ra.addFlashAttribute("errorMessage", ex.getMessage());
                return redirectToEntry(bookingId, buildingId);
            }
            return "receipts/entry";
        } catch (ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return redirectToEntry(bookingId, buildingId);
        }
    }

    @GetMapping("/{id}/print")
    public String print(
            @PathVariable UUID id, @RequestParam UUID bookingId, Model model) {
        Receipt receipt = receiptService.getForPrint(id, bookingId);
        model.addAttribute("receipt", receipt);
        model.addAttribute("booking", receipt.getBooking());
        model.addAttribute("pageTitle", "Receipt " + (receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : id));
        receiptPrintService.addPrintAttributes(model, receipt);
        return "receipts/print";
    }

    private void addPageTitleAndPicker(Model model, UUID buildingId, UUID bookingId) {
        model.addAttribute("pageTitle", "Receipts entry");
        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        UUID builderId = TenantContext.requireBuilderId();
        List<Booking> bookings =
                buildingId == null
                        ? bookingRepository.findActiveForPaymentSchedule(builderId)
                        : bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        model.addAttribute("bookings", bookings);
        model.addAttribute("selectedBookingId", bookingId);
    }

    private void addReceiptWorkspace(Model model, UUID buildingId, UUID bookingId, Receipt receiptForm) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("receiptForm", receiptForm);
        model.addAttribute("summary", receiptService.summarizeBooking(bookingId));
        List<Receipt> history = receiptService.listHistoryForBooking(bookingId);
        model.addAttribute("receiptHistory", history);
        model.addAttribute(
                "historyTotal",
                history.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getDishonoured()))
                        .map(Receipt::getAmount)
                        .reduce(ZERO, BigDecimal::add));
        model.addAttribute("latestReceiptNumber", receiptService.latestReceiptNumberHint(bookingId).orElse(null));
        boolean editingReceipt = receiptForm.getId() != null;
        model.addAttribute("editingReceipt", editingReceipt);
        if (!editingReceipt) {
            model.addAttribute(
                    "nextReceiptNumberPreview", receiptService.previewNextReceiptNumber(bookingId));
        }
        model.addAttribute("bankAccounts", bankService.listActiveForReceipts());
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

    private static String redirectToEntry(UUID bookingId, UUID buildingId) {
        StringBuilder sb = new StringBuilder("redirect:/receipts?bookingId=").append(bookingId);
        if (buildingId != null) {
            sb.append("&buildingId=").append(buildingId);
        }
        return sb.toString();
    }
}
