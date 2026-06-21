package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.Broker;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.User;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.util.FlatUnitTypes;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BookingSlabPaymentRepository;
import com.floor21.repository.BrokerRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.CancellationRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.repository.ExtraExpenseRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.repository.UserRepository;
import com.floor21.repository.VaultBookingProfileRepository;
import com.floor21.repository.VaultEntryRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int BOOKINGS_DEFAULT_PAGE_SIZE = 10;
    public static final int BOOKINGS_MAX_PAGE_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final FlatRepository flatRepository;
    private final ClientRepository clientRepository;
    private final BrokerRepository brokerRepository;
    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final PartnerFlatAllocationService partnerFlatAllocationService;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final BookingOwnerService bookingOwnerService;
    private final BookingSlabPaymentRepository bookingSlabPaymentRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final ReceiptRepository receiptRepository;
    private final CancellationRepository cancellationRepository;
    private final ExtraExpenseRepository extraExpenseRepository;
    private final VaultBookingProfileRepository vaultBookingProfileRepository;
    private final VaultEntryRepository vaultEntryRepository;

    /** Residential flats the current user may book (partner allocation + availability rules). */
    @Transactional(readOnly = true)
    public List<Flat> listFlatsForBookingForm(UUID builderId) {
        List<Flat> flats =
                flatRepository.findBookableResidentialByBuilder_IdAndStatusIn(
                        builderId,
                        FlatUnitTypes.nonBookableUnitTypeCodesUpper(),
                        List.of("AVAILABLE", "HOLD"));
        return flats.stream()
                .filter(
                        f ->
                                f.getBuilding() != null
                                        && partnerFlatAllocationService.isBookableByCurrentUser(
                                                f.getBuilding().getId(),
                                                partnerFlatAllocationService.getAssignedPartnerIdForFlat(
                                                        f.getId())))
                .toList();
    }

    /** Same as {@link #listFlatsForBookingForm(UUID)} but always includes the flat already on the booking (edit form). */
    @Transactional(readOnly = true)
    public List<Flat> listFlatsForBookingFormEdit(UUID builderId, UUID currentFlatId) {
        List<Flat> flats = listFlatsForBookingForm(builderId);
        if (currentFlatId == null || flats.stream().anyMatch(f -> currentFlatId.equals(f.getId()))) {
            return flats;
        }
        return flatRepository
                .findByIdAndBuilder_Id(currentFlatId, builderId)
                .map(
                        current -> {
                            List<Flat> merged = new ArrayList<>(flats.size() + 1);
                            merged.add(current);
                            merged.addAll(flats);
                            return merged;
                        })
                .orElse(flats);
    }

    @Transactional(readOnly = true)
    public Page<Booking> listPage(int page, int size, String q, UUID projectId) {
        int safeSize = Math.min(Math.max(size, 5), BOOKINGS_MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        UUID builderId = TenantContext.getBuilderIdOrNull();
        if (builderId != null) {
            return listPageForTenant(builderId, pageable);
        }
        return listPageForPlatformAdmin(projectId, q, pageable);
    }

    private Page<Booking> listPageForTenant(UUID builderId, Pageable pageable) {
        Set<UUID> allowedBuildings = TenantContext.getAllowedBuildingIdsOrNull();
        boolean restrictedBuildings = allowedBuildings != null && !allowedBuildings.isEmpty();

        if (canViewAllBookings()) {
            if (restrictedBuildings) {
                return bookingRepository.findByBuilder_IdAndFlat_Building_IdInForListUi(
                        builderId, allowedBuildings, pageable);
            }
            return bookingRepository.findByBuilder_IdForListUi(builderId, pageable);
        }
        UUID staffUserId = currentStaffUserId();
        if (staffUserId == null) {
            return Page.empty(pageable);
        }
        if (restrictedBuildings) {
            return bookingRepository.findByBuilder_IdAndExecutive_IdAndFlat_Building_IdInForListUi(
                    builderId, staffUserId, allowedBuildings, pageable);
        }
        return bookingRepository.findByBuilder_IdAndExecutive_IdForListUi(
                builderId, staffUserId, pageable);
    }

    private Page<Booking> listPageForPlatformAdmin(UUID projectId, String search, Pageable pageable) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        if (projectId != null) {
            if (term != null) {
                return bookingRepository.searchByBuilder_IdForListUi(projectId, term, pageable);
            }
            return bookingRepository.findByBuilder_IdForListUi(projectId, pageable);
        }
        if (term != null) {
            return bookingRepository.searchAllForPlatformAdmin(term, pageable);
        }
        return bookingRepository.findAllForPlatformAdminListUi(pageable);
    }

    @Transactional(readOnly = true)
    public Booking getForPlatformAdmin(UUID id) {
        return bookingRepository
                .findByIdForPlatformAdminView(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public boolean canPlatformAdminManageBookingWithoutExecutive(Booking booking) {
        return booking != null && booking.getExecutive() == null;
    }

    @Transactional(readOnly = true)
    public Booking get(UUID id) {
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getFlat() != null
                && booking.getFlat().getBuilding() != null
                && !TenantContext.canAccessBuilding(booking.getFlat().getBuilding().getId())) {
            throw new ResourceNotFoundException("Booking not found");
        }
        if (!canViewAllBookings() && !isOwnedByCurrentStaff(booking)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        return booking;
    }

    private static boolean canViewAllBookings() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return true;
        }
        if (principal.isSuperAdmin()) {
            return true;
        }
        if (principal.getStaffUserId() == null) {
            return true;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUILDER_ADMIN".equals(a.getAuthority()));
    }

    private static UUID currentStaffUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return null;
        }
        return principal.getStaffUserId();
    }

    private static boolean isOwnedByCurrentStaff(Booking booking) {
        UUID staffUserId = currentStaffUserId();
        if (staffUserId == null || booking.getExecutive() == null) {
            return false;
        }
        return staffUserId.equals(booking.getExecutive().getId());
    }

    @Transactional
    public Booking save(Booking form) {
        return save(form, List.of());
    }

    @Transactional
    public Booking save(Booking form, List<UUID> coOwnerIds) {
        UUID builderId = TenantContext.requireBuilderId();
        var builder = builderRepository.findById(builderId).orElseThrow();

        UUID flatId = form.getFlat().getId();
        Flat flat =
                (form.getId() == null
                                ? flatRepository.findByIdAndBuilder_IdForUpdate(flatId, builderId)
                                : flatRepository.findByIdAndBuilder_Id(flatId, builderId))
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        if (flat.getBuilding() != null
                && !TenantContext.canAccessBuilding(flat.getBuilding().getId())) {
            throw new ResourceNotFoundException("Flat not found");
        }
        if (flat.getBuilding() != null) {
            partnerFlatAllocationService.assertCanManageFlat(flat.getBuilding().getId(), flat.getId());
        }
        Client client =
                clientRepository
                        .findByIdAndBuilder_Id(form.getClient().getId(), builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        boolean isNew = form.getId() == null;
        Booking entity;
        if (isNew) {
            if (bookingRepository.countActiveByFlatId(flat.getId()) > 0) {
                throw new IllegalArgumentException("This flat already has an active booking");
            }
            if (!"AVAILABLE".equals(flat.getStatus()) && !"HOLD".equals(flat.getStatus())) {
                throw new IllegalArgumentException("Flat is not available for booking");
            }
            entity = new Booking();
            entity.setCreatedAt(Instant.now());
            entity.setBookingCode(nextBookingCode());
            flat.setStatus("BOOKED");
            flatRepository.save(flat);
        } else {
            entity =
                    bookingRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            if (!canViewAllBookings() && !isOwnedByCurrentStaff(entity)) {
                throw new ResourceNotFoundException("Booking not found");
            }
            if (!entity.getFlat().getId().equals(flat.getId())) {
                throw new IllegalArgumentException("Flat cannot be changed for an existing booking");
            }
        }

        entity.setBuilder(builder);
        entity.setFlat(flat);
        entity.setClient(client);
        entity.setBookingDate(form.getBookingDate() != null ? form.getBookingDate() : LocalDate.now());
        entity.setConsiderationAmt(form.getConsiderationAmt());
        entity.setQuotedAmount(form.getQuotedAmount());
        entity.setBrokerage(zeroIfNull(form.getBrokerage()));
        entity.setTds(zeroIfNull(form.getTds()));
        entity.setGst(zeroIfNull(form.getGst()));
        entity.setFinalAmount(zeroIfNull(form.getFinalAmount()));
        entity.setDueAmountDate(form.getDueAmountDate());
        entity.setBookingIntimationDate(form.getBookingIntimationDate());
        entity.setNocRequestDate(form.getNocRequestDate());
        entity.setMarketValue(form.getMarketValue());
        entity.setStampDutyAmount(form.getStampDutyAmount());
        entity.setRegistrationAmount(form.getRegistrationAmount());
        entity.setFileNo(flatNumberFrom(flat));
        entity.setScheme(form.getScheme());
        entity.setParkingInfo(form.getParkingInfo());
        entity.setReference(form.getReference());
        entity.setBareFlat(form.getBareFlat());
        entity.setParticulars(form.getParticulars());
        if (isNew || entity.getStatus() == null) {
            entity.setStatus("ACTIVE");
        }

        if (form.getBroker() != null && form.getBroker().getId() != null) {
            Broker broker =
                    brokerRepository
                            .findByIdAndBuilder_Id(form.getBroker().getId(), builderId)
                            .orElse(null);
            entity.setBroker(broker);
        } else {
            entity.setBroker(null);
        }

        UUID execId = resolveExecutiveId(form);
        if (execId != null) {
            userRepository
                    .findById(execId)
                    .filter(u -> userProjectAssignmentService.hasMembership(u.getId(), builderId))
                    .ifPresent(entity::setExecutive);
        } else {
            entity.setExecutive(null);
        }

        entity.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(entity);
        bookingOwnerService.syncOwners(saved, coOwnerIds, builderId);
        return saved;
    }

    @Transactional
    public void removeCancelled(UUID id) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_Id(id, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getFlat() != null
                && booking.getFlat().getBuilding() != null
                && !TenantContext.canAccessBuilding(booking.getFlat().getBuilding().getId())) {
            throw new ResourceNotFoundException("Booking not found");
        }
        if (!canViewAllBookings() && !isOwnedByCurrentStaff(booking)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        if (canPlatformAdminManageBookingWithoutExecutive(booking)) {
            deleteBookingRecords(booking, builderId, true);
            return;
        }
        deleteCancelledBooking(booking, builderId);
    }

    @Transactional
    public void removeForPlatformAdminWithoutExecutive(UUID id) {
        Booking booking = getForPlatformAdmin(id);
        if (!canPlatformAdminManageBookingWithoutExecutive(booking)) {
            throw new IllegalArgumentException(
                    "Platform admin can only remove bookings with no executive (Booked by) assigned.");
        }
        deleteBookingRecords(booking, booking.getBuilder().getId(), true);
    }

    private void deleteCancelledBooking(Booking booking, UUID builderId) {
        UUID id = booking.getId();
        if (!"CANCELLED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Only cancelled bookings can be removed.");
        }
        deleteBookingRecords(booking, builderId, false);
    }

    private void deleteBookingRecords(Booking booking, UUID builderId, boolean cascadeFinancialRecords) {
        UUID id = booking.getId();
        if (!cascadeFinancialRecords) {
            if (receiptRepository.countByBooking_IdAndBuilder_Id(id, builderId) > 0) {
                throw new IllegalArgumentException(
                        "This booking has payment receipts. Remove cannot proceed while receipts exist.");
            }
            if (vaultEntryRepository.countByBooking_Id(id) > 0) {
                throw new IllegalArgumentException(
                        "This booking has vault entries. Remove cannot proceed while vault records exist.");
            }
        }

        if ("ACTIVE".equals(booking.getStatus())) {
            if (!cascadeFinancialRecords) {
                throw new IllegalArgumentException("Only cancelled bookings can be removed.");
            }
            Flat flat = booking.getFlat();
            if (flat != null) {
                flat.setStatus("AVAILABLE");
                flatRepository.save(flat);
            }
        } else if (!"CANCELLED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Only active or cancelled bookings can be removed.");
        }

        bookingSlabPaymentRepository.deleteAllForBooking(id);
        receiptRepository.deleteByBooking_IdAndBuilder_Id(id, builderId);
        bookingPaymentSlabRepository.deleteByBooking_Id(id);
        vaultBookingProfileRepository.deleteById(id);
        vaultEntryRepository.deleteByBooking_Id(id);
        extraExpenseRepository.deleteByBooking_Id(id);
        cancellationRepository.deleteByBooking_Id(id);
        bookingRepository.delete(booking);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String flatNumberFrom(Flat flat) {
        if (flat == null || flat.getFlatNumber() == null || flat.getFlatNumber().isBlank()) {
            return null;
        }
        return flat.getFlatNumber().trim();
    }

    private UUID resolveExecutiveId(Booking form) {
        if (form.getExecutive() != null && form.getExecutive().getId() != null) {
            return form.getExecutive().getId();
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Floor21UserPrincipal p) {
            if (!p.isSuperAdmin()) {
                return userRepository
                        .findFirstByEmailIgnoreCaseAndActiveTrue(p.getEmail())
                        .map(User::getId)
                        .orElse(null);
            }
        }
        return null;
    }

    private String nextBookingCode() {
        int year = LocalDate.now().getYear();
        String yearPrefix = String.format("F21-%d-%%", year);
        long seq = bookingRepository.maxBookingCodeSequenceForYear(yearPrefix) + 1;
        return String.format("F21-%d-%04d", year, seq);
    }
}
