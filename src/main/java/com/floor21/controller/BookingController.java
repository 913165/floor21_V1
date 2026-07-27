package com.floor21.controller;

import com.floor21.dto.LinkedParkingSlotDto;
import com.floor21.dto.ParkingSlotOptionDto;
import com.floor21.entity.Booking;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.util.BookingTaxDefaults;
import com.floor21.service.AgreementWordService;
import com.floor21.service.BookingParkingInfoService;
import com.floor21.service.BookingService;
import com.floor21.service.BrokerService;
import com.floor21.service.ClientService;
import com.floor21.service.BookingOwnerService;
import com.floor21.service.FlatService;
import com.floor21.service.ReceiptService;
import com.floor21.service.UserProjectAssignmentService;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingOwnerService bookingOwnerService;
    private final ClientService clientService;
    private final BrokerService brokerService;
    private final BuilderRepository builderRepository;
    private final FlatRepository flatRepository;
    private final ReceiptService receiptService;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final BookingParkingInfoService bookingParkingInfoService;
    private final FlatService flatService;
    private final AgreementWordService agreementWordService;

    @InitBinder("booking")
    public void initBinder(WebDataBinder binder) {
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
        binder.registerCustomEditor(
                UUID.class,
                "client.id",
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
                "flat.id",
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
        booking.setBookingDate(LocalDate.now());
        if (flatId != null) {
            flatRepository
                    .findByIdAndBuilder_Id(flatId, TenantContext.requireBuilderId())
                    .ifPresent(
                            flat -> {
                                booking.setFlat(flat);
                                BookingTaxDefaults.applyToNewBooking(booking, flat);
                            });
        }
        model.addAttribute("pageTitle", "New booking");
        model.addAttribute("booking", booking);
        model.addAttribute("clients", clientService.list());
        model.addAttribute("brokers", brokerService.list());
        var builderId = TenantContext.requireBuilderId();
        model.addAttribute(
                "flats",
                flatId != null
                        ? bookingService.listFlatsForBookingFormEdit(builderId, flatId)
                        : bookingService.listFlatsForBookingForm(builderId));
        model.addAttribute("executives", userProjectAssignmentService.listActiveUsersForProject(builderId));
        model.addAttribute("selectedCoOwnerIds", List.<UUID>of());
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
        model.addAttribute("coOwners", bookingOwnerService.ownersInOrder(booking));
        model.addAttribute("ownersDisplayName", bookingOwnerService.ownersDisplayName(booking));
        model.addAttribute(
                "platformAdminCanManageBooking",
                platformAdminView && bookingService.canPlatformAdminManageBookingWithoutExecutive(booking));
        model.addAttribute("showTaxRecalculateHint", BookingTaxDefaults.needsTaxDefaults(booking));
        if (booking.getFlat() != null) {
            var flat = booking.getFlat();
            var flatId = flat.getId();
            boolean unitIsShop = com.floor21.util.FlatUnitTypes.isShopCode(flat.getBhkType());
            model.addAttribute("unitIsShop", unitIsShop);
            if (!unitIsShop) {
            var linkedParking = bookingParkingInfoService.linkedSlotsForResidentialFlat(flatId);
            var parkingDisplay = com.floor21.util.LinkedParkingFormatter.formatSummary(linkedParking);
            if (!platformAdminView
                    && parkingDisplay != null
                    && (booking.getParkingInfo() == null || booking.getParkingInfo().isBlank())) {
                bookingParkingInfoService.syncForResidentialFlat(flatId);
                booking.setParkingInfo(parkingDisplay);
            }
            UUID parkingBuildingId =
                    flatRepository
                            .findByIdWithBuilding(flatId)
                            .map(f -> f.getBuilding() != null ? f.getBuilding().getId() : null)
                            .orElse(
                                    booking.getFlat().getBuilding() != null
                                            ? booking.getFlat().getBuilding().getId()
                                            : null);
            model.addAttribute("linkedParking", linkedParking);
            model.addAttribute("parkingDisplay", parkingDisplay);
            model.addAttribute("parkingFlatId", flatId);
            model.addAttribute("parkingBuildingId", parkingBuildingId);
            model.addAttribute("parkingLinksEditable", !platformAdminView && isParkingLinkEnabled());
            if (parkingBuildingId != null && !platformAdminView && isParkingLinkEnabled()) {
                Set<UUID> linkedIds =
                        linkedParking.stream()
                                .map(LinkedParkingSlotDto::parkingFlatId)
                                .collect(Collectors.toSet());
                List<ParkingSlotOptionDto> availableParkingSlots =
                        flatService.listParkingSlotsForLink(parkingBuildingId, flatId).stream()
                                .filter(opt -> !linkedIds.contains(opt.id()))
                                .filter(opt -> opt.linkedResidentialFlatId() == null)
                                .toList();
                model.addAttribute("availableParkingSlots", availableParkingSlots);
            } else {
                model.addAttribute("availableParkingSlots", List.<ParkingSlotOptionDto>of());
            }
            }
        }
        return "bookings/detail";
    }

    @GetMapping("/{id}/agreement")
    public ResponseEntity<byte[]> downloadAgreement(@PathVariable UUID id) {
        Booking booking = bookingService.get(id);
        byte[] data = agreementWordService.generate(id);
        String filename = agreementWordService.suggestedFilename(booking);
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(data.length)
                .body(data);
    }

    @GetMapping("/{id}/linked-parking")
    @ResponseBody
    public ResponseEntity<?> linkedParkingForBooking(@PathVariable UUID id) {
        try {
            Booking booking =
                    isPlatformAdmin()
                            ? bookingService.getForPlatformAdmin(id)
                            : bookingService.get(id);
            if (booking.getFlat() == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(
                    bookingParkingInfoService.linkedSlotsForResidentialFlat(booking.getFlat().getId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/parking-slots-for-link")
    @ResponseBody
    public ResponseEntity<?> parkingSlotsForBooking(@PathVariable UUID id) {
        try {
            Booking booking =
                    isPlatformAdmin()
                            ? bookingService.getForPlatformAdmin(id)
                            : bookingService.get(id);
            if (booking.getFlat() == null) {
                return ResponseEntity.ok(List.of());
            }
            UUID flatId = booking.getFlat().getId();
            UUID buildingId =
                    flatRepository
                            .findByIdWithBuilding(flatId)
                            .map(f -> f.getBuilding().getId())
                            .orElseThrow(() -> new IllegalArgumentException("Building not found for this flat."));
            return ResponseEntity.ok(flatService.listParkingSlotsForLink(buildingId, flatId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private static boolean isParkingLinkEnabled() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal)) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(
                        a -> {
                            String role = a.getAuthority();
                            return "ROLE_SUPER_ADMIN".equals(role)
                                    || "ROLE_BUILDER_ADMIN".equals(role)
                                    || "ROLE_EXECUTIVE".equals(role);
                        });
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
                bookingService.listFlatsForBookingFormEdit(
                        builderId, booking.getFlat() != null ? booking.getFlat().getId() : null));
        model.addAttribute("executives", userProjectAssignmentService.listActiveUsersForProject(builderId));
        model.addAttribute("selectedCoOwnerIds", bookingOwnerService.listCoOwnerIds(id));
        model.addAttribute("showTaxRecalculateHint", BookingTaxDefaults.needsTaxDefaults(booking));
        return "bookings/form";
    }

    @PostMapping("/{id}/recalculate-taxes")
    public String recalculateTaxes(@PathVariable UUID id, RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings/" + id;
        }
        try {
            bookingService.recalculateTaxes(id);
            ra.addFlashAttribute("successMessage", "TDS, GST, and final amount recalculated from consideration.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/bookings/" + id;
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute Booking booking,
            @RequestParam(required = false) List<UUID> coOwnerIds,
            RedirectAttributes ra) {
        if (isPlatformAdmin()) {
            ra.addFlashAttribute("errorMessage", "Read-only for platform admin.");
            return "redirect:/bookings";
        }
        try {
            Booking saved = bookingService.save(booking, coOwnerIds != null ? coOwnerIds : List.of());
            ra.addFlashAttribute("successMessage", "Booking saved");
            return "redirect:/bookings/" + saved.getId();
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return booking.getId() != null
                    ? "redirect:/bookings/" + booking.getId() + "/edit"
                    : "redirect:/bookings/new";
        }
    }

    @PostMapping("/{id}/remove")
    public String remove(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID projectId,
            RedirectAttributes ra) {
        try {
            if (isPlatformAdmin()) {
                bookingService.removeForPlatformAdminWithoutExecutive(id);
            } else {
                bookingService.removeCancelled(id);
            }
            ra.addFlashAttribute("successMessage", "Booking removed");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/bookings/" + id + (projectId != null ? "?projectId=" + projectId : "");
        }
        return projectId != null ? "redirect:/bookings?projectId=" + projectId : "redirect:/bookings";
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
