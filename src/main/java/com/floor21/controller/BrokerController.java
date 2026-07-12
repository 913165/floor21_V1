package com.floor21.controller;

import com.floor21.dto.BrokerQuickCreateRequest;
import com.floor21.dto.BrokerQuickCreateResponse;
import com.floor21.entity.Broker;
import com.floor21.repository.BookingRepository;
import com.floor21.security.TenantContext;
import com.floor21.service.BrokerService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/brokers")
@RequiredArgsConstructor
public class BrokerController {

    private final BrokerService brokerService;
    private final BookingRepository bookingRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Brokers");
        model.addAttribute("brokers", brokerService.list());
        return "brokers/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New Broker");
        model.addAttribute("broker", new Broker());
        return "brokers/form";
    }

    @GetMapping("/{id}/bookings")
    public String brokerBookings(@PathVariable UUID id, Model model) {
        var broker = brokerService.get(id);
        model.addAttribute("pageTitle", "Broker Bookings");
        model.addAttribute("broker", broker);
        model.addAttribute(
                "bookings",
                bookingRepository.findByBroker_IdAndBuilder_IdForListUi(
                        id, TenantContext.requireBuilderId()));
        return "brokers/bookings";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Broker broker, RedirectAttributes ra) {
        brokerService.save(broker);
        ra.addFlashAttribute("successMessage", "Broker saved");
        return "redirect:/brokers";
    }

    @PostMapping("/quick")
    @PreAuthorize("hasAnyRole('BUILDER_ADMIN','EXECUTIVE')")
    @ResponseBody
    public ResponseEntity<?> quickCreate(@RequestBody BrokerQuickCreateRequest request) {
        try {
            Broker saved = brokerService.quickCreate(request);
            return ResponseEntity.ok(new BrokerQuickCreateResponse(saved.getId(), saved.getFullName()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Broker");
        model.addAttribute("broker", brokerService.get(id));
        return "brokers/form";
    }
}
