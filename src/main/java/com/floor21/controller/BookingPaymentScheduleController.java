package com.floor21.controller;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.service.BookingPaymentSlabService;
import com.floor21.service.BuildingService;
import com.floor21.service.PaymentSlabTemplateService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings/payment-schedule")
@RequiredArgsConstructor
public class BookingPaymentScheduleController {

    private final BuildingService buildingService;
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final PaymentSlabTemplateService paymentSlabTemplateService;

    @GetMapping
    public String page(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            Model model) {
        model.addAttribute("pageTitle", "Slabs — payment schedule");
        model.addAttribute("platformMilestones", paymentSlabTemplateService.listForBuilderReference());
        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("bookings", bookingPaymentSlabService.listBookingsForSchedule(buildingId));
        model.addAttribute("selectedBookingId", bookingId);
        if (bookingId != null) {
            Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
            model.addAttribute("selectedBooking", booking);
            var base = bookingPaymentSlabService.baseConsideration(booking);
            model.addAttribute("baseAmount", base);
            var rows = bookingPaymentSlabService.listLines(bookingId);
            model.addAttribute("rows", rows);
            BookingPaymentSlabBatchForm saveForm = new BookingPaymentSlabBatchForm();
            saveForm.setBookingId(bookingId);
            for (BookingPaymentSlab r : rows) {
                BookingPaymentSlabBatchForm.Line line = new BookingPaymentSlabBatchForm.Line();
                line.setId(r.getId());
                if (r.getDueDate() != null) {
                    line.setDueDay(r.getDueDate().getDayOfMonth());
                    line.setDueMonth(r.getDueDate().getMonthValue());
                    line.setDueYear(r.getDueDate().getYear());
                }
                line.setMilestoneLabel(r.getMilestoneLabel());
                line.setPercent(r.getPercent());
                line.setAgreedAmount(r.getAgreedAmount());
                line.setExtraAmount(r.getExtraAmount());
                saveForm.getLines().add(line);
            }
            model.addAttribute("saveForm", saveForm);
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

    @PostMapping("/save")
    public String save(
            @ModelAttribute("saveForm") BookingPaymentSlabBatchForm form,
            @RequestParam(required = false) UUID buildingId,
            RedirectAttributes ra) {
        try {
            bookingPaymentSlabService.saveLines(form);
            ra.addFlashAttribute("successMessage", "Payment schedule saved.");
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
}
