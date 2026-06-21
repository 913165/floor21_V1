package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.BookingService;
import com.floor21.service.CancellationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CancellationController {

    private final CancellationService cancellationService;
    private final BookingService bookingService;

    @GetMapping("/cancellations")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Cancellations");
        model.addAttribute("cancellations", cancellationService.list());
        return "cancellations/list";
    }

    @GetMapping("/bookings/{id}/cancel")
    public String cancelForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        Booking booking =
                isPlatformAdmin() ? bookingService.getForPlatformAdmin(id) : bookingService.get(id);
        if (isPlatformAdmin() && booking.getExecutive() != null) {
            ra.addFlashAttribute(
                    "errorMessage",
                    "Platform admin can only cancel bookings with no executive (Booked by) assigned.");
            return "redirect:/bookings/" + id;
        }
        model.addAttribute("pageTitle", "Cancel Booking");
        model.addAttribute("booking", booking);
        return "cancellations/form";
    }

    @PostMapping("/bookings/{id}/cancel/confirm")
    public String confirm(
            @PathVariable UUID id,
            @RequestParam LocalDate cancelDate,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) BigDecimal refundAmount,
            RedirectAttributes ra) {
        try {
            if (isPlatformAdmin()) {
                cancellationService.cancelBookingForPlatformAdmin(id, cancelDate, reason, refundAmount);
            } else {
                cancellationService.cancelBooking(id, cancelDate, reason, refundAmount);
            }
            ra.addFlashAttribute("successMessage", "Booking cancelled");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/bookings/" + id;
        }
        return "redirect:/bookings";
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
