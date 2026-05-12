package com.floor21.controller;

import com.floor21.service.BookingService;
import com.floor21.service.CancellationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public String cancelForm(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Cancel Booking");
        model.addAttribute("booking", bookingService.get(id));
        return "cancellations/form";
    }

    @PostMapping("/bookings/{id}/cancel/confirm")
    public String confirm(
            @PathVariable UUID id,
            @RequestParam LocalDate cancelDate,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) BigDecimal refundAmount,
            RedirectAttributes ra) {
        cancellationService.cancelBooking(id, cancelDate, reason, refundAmount);
        ra.addFlashAttribute("successMessage", "Booking cancelled");
        return "redirect:/bookings";
    }
}
