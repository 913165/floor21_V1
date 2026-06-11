package com.floor21.controller;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.SlabScheduleSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Building;
import com.floor21.entity.ExtraExpense;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.BookingPaymentSlabService;
import com.floor21.service.BuildingService;
import com.floor21.service.ExtraExpenseService;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients/milestone-setup")
@RequiredArgsConstructor
public class ClientMilestoneSetupController {

    private final BuildingService buildingService;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ExtraExpenseService extraExpenseService;

    @InitBinder("saveForm")
    public void initSaveFormBinder(WebDataBinder binder) {
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
    }

    @GetMapping
    public String page(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) String clientQ,
            Model model) {
        model.addAttribute("pageTitle", "Milestone setup (Clients)");
        boolean platformAdmin = isPlatformAdmin();
        boolean editable = !platformAdmin;
        model.addAttribute("platformAdminView", platformAdmin);
        model.addAttribute("editable", editable);
        model.addAttribute("filterProjectId", projectId);
        model.addAttribute("clientQ", clientQ != null ? clientQ.trim() : "");

        if (platformAdmin) {
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            model.addAttribute("buildings", buildingService.filterForPlatformAdmin(projectId, null));
        } else {
            model.addAttribute("buildings", buildingService.listForTenant());
        }

        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("selectedBookingId", bookingId);

        if (buildingId == null) {
            return "clients/milestone-setup";
        }

        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            model.addAttribute("errorMessage", "Choose a project or impersonate a partner to load bookings.");
            return "clients/milestone-setup";
        }

        List<Booking> bookings =
                bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        model.addAttribute("bookings", filterBookingsByClientQuery(bookings, clientQ));
        Building building =
                buildingRepository
                        .findByIdWithBuilder(buildingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        model.addAttribute("selectedBuilding", building);

        if (bookingId == null) {
            return "clients/milestone-setup";
        }

        if (!bookingBelongsToBuilding(bookingId, builderId, buildingId)) {
            model.addAttribute("errorMessage", "That client booking is not in the selected building.");
            model.addAttribute("selectedBookingId", null);
            return "clients/milestone-setup";
        }

        Booking booking = loadBookingForView(bookingId, builderId);
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("baseAmount", bookingPaymentSlabService.baseConsideration(booking));
        model.addAttribute("extraExpensesTotal", sumExtraExpenses(bookingId, builderId));
        model.addAttribute(
                "milestoneTemplatesAvailable",
                bookingPaymentSlabService.hasBuildingMilestoneTemplates(buildingId));

        if (editable) {
            boolean created = bookingPaymentSlabService.prepareClientMilestoneSetup(bookingId);
            if (created) {
                model.addAttribute(
                        "successMessage",
                        "Milestone schedule calculated from the building template and booking amount.");
            }
        }

        List<BookingPaymentSlab> slabs = listSlabsForView(bookingId, builderId);
        model.addAttribute("slabs", slabs);
        model.addAttribute("saveForm", buildSaveForm(bookingId, slabs));
        SlabScheduleSummary summary = summarizeForView(bookingId, builderId, booking);
        model.addAttribute("scheduleSummary", summary);

        return "clients/milestone-setup";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute("saveForm") BookingPaymentSlabBatchForm form,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            RedirectAttributes ra) {
        try {
            int saved = bookingPaymentSlabService.saveLines(form);
            ra.addFlashAttribute("successMessage", "Milestone schedule saved (" + saved + " rows).");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(form.getBookingId(), buildingId, projectId);
    }

    @PostMapping("/materialize")
    public String materialize(
            @RequestParam UUID bookingId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String replace,
            RedirectAttributes ra) {
        boolean doReplace =
                replace != null && ("true".equalsIgnoreCase(replace) || "on".equalsIgnoreCase(replace));
        try {
            bookingPaymentSlabService.materializeFromTemplates(bookingId, doReplace);
            ra.addFlashAttribute(
                    "successMessage",
                    doReplace
                            ? "Milestones replaced from the building template."
                            : "Milestones set from the building template.");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectBack(bookingId, buildingId, projectId);
    }

    private BookingPaymentSlabBatchForm buildSaveForm(UUID bookingId, List<BookingPaymentSlab> slabs) {
        BookingPaymentSlabBatchForm form = new BookingPaymentSlabBatchForm();
        form.setBookingId(bookingId);
        List<BookingPaymentSlabBatchForm.Line> lines = new ArrayList<>();
        for (BookingPaymentSlab slab : slabs) {
            BookingPaymentSlabBatchForm.Line line = new BookingPaymentSlabBatchForm.Line();
            line.setId(slab.getId());
            line.setDueDate(slab.getDueDate());
            line.setMilestoneLabel(slab.getMilestoneLabel());
            line.setPercent(slab.getPercent());
            line.setAgreedAmount(slab.getAgreedAmount());
            line.setExtraAmount(slab.getExtraAmount());
            lines.add(line);
        }
        form.setLines(lines);
        return form;
    }

    private List<Booking> filterBookingsByClientQuery(List<Booking> bookings, String clientQ) {
        if (clientQ == null || clientQ.isBlank()) {
            return bookings;
        }
        String term = clientQ.trim().toLowerCase();
        return bookings.stream()
                .filter(
                        b -> {
                            if (b.getClient() == null) {
                                return false;
                            }
                            String flat =
                                    b.getFlat() != null && b.getFlat().getFlatNumber() != null
                                            ? b.getFlat().getFlatNumber().toLowerCase()
                                            : "";
                            String name = b.getClient().displayName().toLowerCase();
                            String code =
                                    b.getBookingCode() != null ? b.getBookingCode().toLowerCase() : "";
                            return name.contains(term) || flat.contains(term) || code.contains(term);
                        })
                .toList();
    }

    private UUID resolveBuilderId(UUID buildingId, UUID projectId) {
        UUID tenant = TenantContext.getBuilderIdOrNull();
        if (tenant != null) {
            return tenant;
        }
        if (buildingId != null) {
            Building building = buildingRepository.findByIdWithBuilder(buildingId).orElse(null);
            if (building != null && building.getBuilder() != null) {
                return building.getBuilder().getId();
            }
        }
        return projectId;
    }

    private boolean bookingBelongsToBuilding(UUID bookingId, UUID builderId, UUID buildingId) {
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .filter(
                        b ->
                                b.getFlat() != null
                                        && b.getFlat().getBuilding() != null
                                        && buildingId.equals(b.getFlat().getBuilding().getId()))
                .isPresent();
    }

    private Booking loadBookingForView(UUID bookingId, UUID builderId) {
        if (TenantContext.getBuilderIdOrNull() != null) {
            return bookingPaymentSlabService.getBookingForSchedule(bookingId);
        }
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private List<BookingPaymentSlab> listSlabsForView(UUID bookingId, UUID builderId) {
        if (TenantContext.getBuilderIdOrNull() != null) {
            return bookingPaymentSlabService.listLines(bookingId);
        }
        loadBookingForView(bookingId, builderId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    /** Read-only totals for the setup grid — must not call payment-schedule sync (which deletes rows). */
    private SlabScheduleSummary summarizeForView(UUID bookingId, UUID builderId, Booking booking) {
        List<BookingPaymentSlab> slabs = listSlabsForView(bookingId, builderId);
        BigDecimal agreed = BigDecimal.ZERO;
        BigDecimal extra = BigDecimal.ZERO;
        BigDecimal percent = BigDecimal.ZERO;
        for (BookingPaymentSlab slab : slabs) {
            if (slab.getAgreedAmount() != null) {
                agreed = agreed.add(slab.getAgreedAmount());
            }
            if (slab.getExtraAmount() != null) {
                extra = extra.add(slab.getExtraAmount());
            }
            if (slab.getPercent() != null) {
                percent = percent.add(slab.getPercent());
            }
        }
        BigDecimal consideration = bookingPaymentSlabService.baseConsideration(booking);
        BigDecimal remaining =
                consideration != null && consideration.signum() > 0
                        ? consideration.subtract(agreed)
                        : null;
        return new SlabScheduleSummary(
                agreed, extra, agreed.add(extra), percent, consideration, remaining, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal sumExtraExpenses(UUID bookingId, UUID builderId) {
        try {
            if (TenantContext.getBuilderIdOrNull() != null) {
                return extraExpenseService.list(bookingId).stream()
                        .map(ExtraExpense::getAmount)
                        .filter(a -> a != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        } catch (IllegalStateException ignored) {
            /* platform admin without tenant */
        }
        return BigDecimal.ZERO;
    }

    private static String redirectBack(UUID bookingId, UUID buildingId, UUID projectId) {
        StringBuilder sb = new StringBuilder("redirect:/clients/milestone-setup?");
        if (projectId != null) {
            sb.append("projectId=").append(projectId).append("&");
        }
        if (buildingId != null) {
            sb.append("buildingId=").append(buildingId).append("&");
        }
        if (bookingId != null) {
            sb.append("bookingId=").append(bookingId);
        }
        String url = sb.toString();
        if (url.endsWith("?") || url.endsWith("&")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
