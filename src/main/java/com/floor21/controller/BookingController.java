package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.UserProjectAssignmentService;
import com.floor21.util.FlatUnitTypes;
import com.floor21.service.BookingService;
import com.floor21.service.BrokerService;
import com.floor21.service.ClientService;
import com.floor21.service.ReceiptService;
import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final BrokerService brokerService;
    private final BuilderRepository builderRepository;
    private final FlatRepository flatRepository;
    private final ReceiptService receiptService;
    private final UserProjectAssignmentService userProjectAssignmentService;

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
    public String list(
            Model model,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        boolean platformAdminView = isPlatformAdmin();
        Page<Booking> bookingPage = bookingService.listPage(page, size, q, projectId);
        model.addAttribute("pageTitle", "Bookings");
        model.addAttribute("platformAdminView", platformAdminView);
        model.addAttribute("readonlyView", platformAdminView);
        model.addAttribute("bookingPage", bookingPage);
        model.addAttribute("bookings", bookingPage.getContent());
        model.addAttribute("pageSize", bookingPage.getSize());
        model.addAttribute("pageSizeOptions", List.of(10, 25, 50));
        model.addAttribute("filterSearch", q != null ? q.trim() : "");
        if (platformAdminView) {
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            model.addAttribute("filterProjectId", projectId);
        }
        return "bookings/list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID flatId, Model model, RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings";
        }
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
        model.addAttribute("executives", userProjectAssignmentService.listActiveUsersForProject(builderId));
        return "bookings/form";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID projectId,
            Model model) {
        boolean platformAdminView = isPlatformAdmin();
        Booking booking =
                platformAdminView ? bookingService.getForPlatformAdmin(id) : bookingService.get(id);
        model.addAttribute("pageTitle", "Booking " + booking.getBookingCode());
        model.addAttribute("booking", booking);
        model.addAttribute("platformAdminView", platformAdminView);
        model.addAttribute("readonlyView", platformAdminView);
        model.addAttribute("filterProjectId", projectId);
        UUID builderId = booking.getBuilder() != null ? booking.getBuilder().getId() : null;
        model.addAttribute(
                "receiptTotal",
                platformAdminView
                        ? receiptService.totalForBooking(id, builderId)
                        : receiptService.totalForBooking(id));
        return "bookings/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings/" + id;
        }
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
        model.addAttribute("executives", userProjectAssignmentService.listActiveUsersForProject(builderId));
        return "bookings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Booking booking, RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings";
        }
        Booking saved = bookingService.save(booking);
        ra.addFlashAttribute("successMessage", "Booking saved");
        return "redirect:/bookings/" + saved.getId();
    }

    @PostMapping("/{id}/remove")
    public String remove(@PathVariable UUID id, RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings/" + id;
        }
        try {
            bookingService.removeCancelled(id);
            ra.addFlashAttribute("successMessage", "Booking removed");
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
