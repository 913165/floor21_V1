package com.floor21.controller;

import com.floor21.entity.Receipt;
import com.floor21.service.BookingService;
import com.floor21.service.ReceiptService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings/{bookingId}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final BookingService bookingService;

    @GetMapping
    public String list(@PathVariable UUID bookingId, Model model) {
        var booking = bookingService.get(bookingId);
        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("booking", booking);
        model.addAttribute("receipts", receiptService.listForBooking(bookingId));
        return "receipts/list";
    }

    @GetMapping("/new")
    public String form(@PathVariable UUID bookingId, Model model) {
        model.addAttribute("pageTitle", "New Receipt");
        model.addAttribute("booking", bookingService.get(bookingId));
        Receipt r = new Receipt();
        r.setReceiptDate(java.time.LocalDate.now());
        model.addAttribute("receipt", r);
        return "receipts/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable UUID bookingId, @ModelAttribute Receipt receipt, RedirectAttributes ra) {
        receiptService.save(bookingId, receipt);
        ra.addFlashAttribute("successMessage", "Receipt saved");
        return "redirect:/bookings/" + bookingId + "/receipts";
    }
}
