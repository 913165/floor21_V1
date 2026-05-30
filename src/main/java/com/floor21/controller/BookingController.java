package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.UserRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.FlatUnitTypes;
import com.floor21.service.BookingService;
import com.floor21.service.BrokerService;
import com.floor21.service.ClientService;
import com.floor21.service.ReceiptService;
import java.beans.PropertyEditorSupport;
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
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final BrokerService brokerService;
    private final FlatRepository flatRepository;
    private final UserRepository userRepository;
    private final ReceiptService receiptService;

    @InitBinder("booking")
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                UUID.class,
                "broker.id",
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
                "executive.id",
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

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Bookings");
        model.addAttribute("bookings", bookingService.list());
        return "bookings/list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID flatId, Model model) {
        Booking booking = new Booking();
        if (flatId != null) {
            flatRepository
                    .findByIdAndBuilder_Id(flatId, TenantContext.requireBuilderId())
                    .ifPresent(booking::setFlat);
        }
        model.addAttribute("pageTitle", "New booking");
        model.addAttribute("booking", booking);
        model.addAttribute("clients", clientService.list());
        model.addAttribute("brokers", brokerService.list());
        var builderId = TenantContext.requireBuilderId();
        model.addAttribute(
                "flats",
                flatRepository.findBookableResidentialByBuilder_IdAndStatusIn(
                        builderId, FlatUnitTypes.amenityCodesUpper(), List.of("AVAILABLE", "HOLD")));
        model.addAttribute("executives", userRepository.findByBuilder_IdAndActiveTrueOrderByFullNameAsc(builderId));
        return "bookings/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var booking = bookingService.get(id);
        model.addAttribute("pageTitle", "Booking " + booking.getBookingCode());
        model.addAttribute("booking", booking);
        model.addAttribute("receiptTotal", receiptService.totalForBooking(id));
        return "bookings/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        var booking = bookingService.get(id);
        model.addAttribute("pageTitle", "Modify booking particulars");
        model.addAttribute("booking", booking);
        model.addAttribute("clients", clientService.list());
        model.addAttribute("brokers", brokerService.list());
        var builderId = TenantContext.requireBuilderId();
        model.addAttribute(
                "flats",
                flatRepository.findBookableResidentialByBuilder_IdAndStatusIn(
                        builderId,
                        FlatUnitTypes.amenityCodesUpper(),
                        List.of("AVAILABLE", "HOLD", "BOOKED")));
        model.addAttribute("executives", userRepository.findByBuilder_IdAndActiveTrueOrderByFullNameAsc(builderId));
        return "bookings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Booking booking, RedirectAttributes ra) {
        Booking saved = bookingService.save(booking);
        ra.addFlashAttribute("successMessage", "Booking saved");
        return "redirect:/bookings/" + saved.getId();
    }
}
