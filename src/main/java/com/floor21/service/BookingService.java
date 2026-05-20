package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.Broker;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.User;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BrokerRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.UserRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlatRepository flatRepository;
    private final ClientRepository clientRepository;
    private final BrokerRepository brokerRepository;
    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final PartnerFlatAllocationService partnerFlatAllocationService;

    @Transactional(readOnly = true)
    public List<Booking> list() {
        List<Booking> all = bookingRepository.findByBuilder_IdForListUi(TenantContext.requireBuilderId());
        if (TenantContext.hasUnrestrictedBuildingAccess()) {
            return all;
        }
        return all.stream()
                .filter(
                        b ->
                                b.getFlat() != null
                                        && b.getFlat().getBuilding() != null
                                        && TenantContext.canAccessBuilding(
                                                b.getFlat().getBuilding().getId()))
                .toList();
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
        return booking;
    }

    @Transactional
    public Booking save(Booking form) {
        UUID builderId = TenantContext.requireBuilderId();
        var builder = builderRepository.findById(builderId).orElseThrow();

        Flat flat =
                flatRepository
                        .findByIdAndBuilder_Id(form.getFlat().getId(), builderId)
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
            if (!"AVAILABLE".equals(flat.getStatus()) && !"HOLD".equals(flat.getStatus())) {
                throw new IllegalArgumentException("Flat is not available for booking");
            }
            entity = new Booking();
            entity.setCreatedAt(Instant.now());
            entity.setBookingCode(nextBookingCode(builderId));
            flat.setStatus("BOOKED");
            flatRepository.save(flat);
        } else {
            entity =
                    bookingRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
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
        entity.setFileNo(form.getFileNo());
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
                    .filter(u -> u.getBuilder().getId().equals(builderId))
                    .ifPresent(entity::setExecutive);
        } else {
            entity.setExecutive(null);
        }

        entity.setUpdatedAt(Instant.now());
        return bookingRepository.save(entity);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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

    private String nextBookingCode(UUID builderId) {
        int year = LocalDate.now().getYear();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        long seq = bookingRepository.countByBuilder_IdAndBookingDateBetween(builderId, start, end) + 1;
        return String.format("F21-%d-%04d", year, seq);
    }
}
