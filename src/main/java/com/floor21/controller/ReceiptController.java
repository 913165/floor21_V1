package com.floor21.controller;

import com.floor21.entity.Receipt;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.service.ReceiptService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Legacy URLs under {@code /bookings/{id}/receipts}; redirect to {@link ReceiptsHubController}.
 */
@Controller
@RequestMapping("/bookings/{bookingId}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public String list(@PathVariable UUID bookingId) {
        return "redirect:/receipts?bookingId=" + bookingId;
    }

    @GetMapping("/new")
    public String form(@PathVariable UUID bookingId) {
        return "redirect:/receipts?bookingId=" + bookingId;
    }

    @PostMapping("/save")
    public String save(
            @PathVariable UUID bookingId,
            @ModelAttribute Receipt receipt,
            RedirectAttributes ra) {
        try {
            boolean updating = receipt.getId() != null;
            receiptService.save(bookingId, receipt);
            ra.addFlashAttribute("successMessage", updating ? "Receipt updated." : "Receipt saved.");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/receipts?bookingId=" + bookingId;
    }
}
