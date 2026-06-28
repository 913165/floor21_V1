package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.service.AllotteeLedgerService;
import com.floor21.service.BookingPaymentSlabService;
import com.floor21.service.BuildingService;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/bookings/allottee-ledger")
@RequiredArgsConstructor
public class AllotteeLedgerController {

    private final BuildingService buildingService;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final AllotteeLedgerService allotteeLedgerService;

    @GetMapping
    public String page(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID bookingId,
            HttpSession session,
            Model model) {
        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, projectId, buildingId, bookingId);
        projectId = selection.projectId();
        buildingId = selection.buildingId();
        bookingId = selection.bookingId();
        boolean platformAdminView = isPlatformAdmin();
        model.addAttribute("pageTitle", "Allottee Ledger");
        model.addAttribute("platformAdminView", platformAdminView);
        model.addAttribute("readonlyView", platformAdminView);
        model.addAttribute("filterProjectId", projectId);

        if (buildingId == null && bookingId != null) {
            UUID builderIdForInfer =
                    platformAdminView
                            ? resolveBuilderId(buildingId, projectId)
                            : TenantContext.requireBuilderId();
            selection =
                    MilestoneNavSession.withInferredBuilding(
                            selection,
                            MilestoneNavSupport.inferBuildingId(
                                    bookingRepository, bookingId, builderIdForInfer));
            buildingId = selection.buildingId();
            bookingId = selection.bookingId();
        }

        if (platformAdminView) {
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            UUID requestedBuildingId = buildingId;
            buildingId = buildingService.sanitizeBuildingIdForProject(buildingId, projectId);
            if (projectId == null) {
                buildingId = null;
                bookingId = null;
            } else if (requestedBuildingId != null && buildingId == null) {
                bookingId = null;
            }
            model.addAttribute("buildings", buildingService.listBuildingsForPlatformProject(projectId));
            model.addAttribute("selectedBuildingId", buildingId);
            model.addAttribute("selectedBookingId", bookingId);
            List<Booking> bookings = listBookingsForPlatformAdmin(buildingId, projectId);
            Booking selectedForList = null;
            if (bookingId != null) {
                UUID builderId = resolveBuilderId(buildingId, projectId);
                if (builderId != null) {
                    try {
                        selectedForList =
                                bookingPaymentSlabService.getBookingForScheduleReadOnly(
                                        bookingId, builderId);
                    } catch (ResourceNotFoundException ignored) {
                        selectedForList = null;
                    }
                }
            }
            model.addAttribute("bookings", MilestoneNavSupport.ensureSelectedBooking(bookings, selectedForList));
            if (bookingId != null) {
                loadSelectedBookingForPlatformAdmin(model, projectId, buildingId, bookingId);
                Object effectiveBuilding = model.getAttribute("selectedBuildingId");
                if (effectiveBuilding instanceof UUID effectiveBuildingId) {
                    buildingId = effectiveBuildingId;
                }
            }
            MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
            return "bookings/allottee-ledger";
        }

        model.addAttribute("buildings", buildingService.listForTenant());
        model.addAttribute("selectedBuildingId", buildingId);
        List<Booking> bookings = bookingPaymentSlabService.listBookingsForSchedule(buildingId);
        Booking selectedForList = null;
        if (bookingId != null) {
            try {
                selectedForList = bookingPaymentSlabService.getBookingForSchedule(bookingId);
            } catch (ResourceNotFoundException ignored) {
                selectedForList = null;
            }
        }
        model.addAttribute("bookings", MilestoneNavSupport.ensureSelectedBooking(bookings, selectedForList));
        model.addAttribute("selectedBookingId", bookingId);
        if (bookingId != null) {
            loadSelectedBookingForTenant(model, buildingId, bookingId);
            Object effectiveBuilding = model.getAttribute("selectedBuildingId");
            if (effectiveBuilding instanceof UUID effectiveBuildingId) {
                buildingId = effectiveBuildingId;
            }
        }
        MilestoneNavSession.remember(session, projectId, buildingId, bookingId);
        return "bookings/allottee-ledger";
    }

    private void loadSelectedBookingForTenant(Model model, UUID buildingId, UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        UUID effectiveBuildingId = buildingId;
        if (effectiveBuildingId == null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null) {
            effectiveBuildingId = booking.getFlat().getBuilding().getId();
        }
        if (effectiveBuildingId != null) {
            model.addAttribute("selectedBuildingId", effectiveBuildingId);
        }
        if (!bookingMatchesBuilding(booking, buildingId)) {
            model.addAttribute(
                    "errorMessage",
                    "That booking is not in the selected building. Choose a booking from the filtered list.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        model.addAttribute("selectedBooking", booking);
        boolean created = bookingPaymentSlabService.prepareSlabMilestones(bookingId);
        if (created) {
            model.addAttribute(
                    "successMessage",
                    "Payment schedule created from milestone templates for this building.");
        }
        model.addAttribute("allotteeLedger", allotteeLedgerService.buildForBooking(bookingId));
        if (booking.getBuilder() != null) {
            model.addAttribute("scheduleBuilderId", booking.getBuilder().getId());
        }
    }

    private void loadSelectedBookingForPlatformAdmin(
            Model model, UUID projectId, UUID buildingId, UUID bookingId) {
        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            model.addAttribute("errorMessage", "Choose a project or building to load a booking.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        if (buildingId != null && !bookingBelongsToBuilding(bookingId, builderId, buildingId)) {
            model.addAttribute("errorMessage", "That booking is not in the selected building.");
            model.addAttribute("selectedBookingId", null);
            return;
        }
        Booking booking = bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
        if (buildingId == null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null) {
            model.addAttribute("selectedBuildingId", booking.getFlat().getBuilding().getId());
        }
        model.addAttribute("selectedBooking", booking);
        model.addAttribute("scheduleBuilderId", builderId);
        model.addAttribute("allotteeLedger", allotteeLedgerService.buildForBookingReadOnly(bookingId, builderId));
    }

    private List<Booking> listBookingsForPlatformAdmin(UUID buildingId, UUID projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        UUID builderId = resolveBuilderId(buildingId, projectId);
        if (builderId == null) {
            return Collections.emptyList();
        }
        if (buildingId == null) {
            return bookingRepository.findActiveForPaymentSchedule(builderId);
        }
        return bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
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

    private static boolean bookingMatchesBuilding(Booking booking, UUID buildingId) {
        if (buildingId == null) {
            return true;
        }
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return false;
        }
        return buildingId.equals(booking.getFlat().getBuilding().getId());
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
