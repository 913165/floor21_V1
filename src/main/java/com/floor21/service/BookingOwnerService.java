package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.BookingOwner;
import com.floor21.entity.Client;
import com.floor21.repository.BookingOwnerRepository;
import com.floor21.repository.ClientRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("bookingOwnerService")
@RequiredArgsConstructor
public class BookingOwnerService {

    private final BookingOwnerRepository bookingOwnerRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<Client> ownersInOrder(UUID bookingId) {
        return bookingOwnerRepository.findByBooking_IdWithClientOrderBySortOrderAsc(bookingId).stream()
                .map(BookingOwner::getClient)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Client> ownersInOrder(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return List.of();
        }
        List<Client> owners = ownersInOrder(booking.getId());
        if (!owners.isEmpty()) {
            return owners;
        }
        if (booking.getClient() != null) {
            return List.of(booking.getClient());
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public String ownersDisplayName(UUID bookingId) {
        return formatOwnersDisplay(ownersInOrder(bookingId));
    }

    @Transactional(readOnly = true)
    public String ownersDisplayName(Booking booking) {
        if (booking == null) {
            return "—";
        }
        if (booking.getId() != null) {
            String fromRows = formatOwnersDisplay(ownersInOrder(booking.getId()));
            if (!fromRows.isBlank() && !"—".equals(fromRows)) {
                return fromRows;
            }
        }
        return booking.getClient() != null ? booking.getClient().displayName() : "—";
    }

    @Transactional(readOnly = true)
    public List<UUID> listCoOwnerIds(UUID bookingId) {
        return bookingOwnerRepository.findByBooking_IdWithClientOrderBySortOrderAsc(bookingId).stream()
                .filter(bo -> BookingOwner.ROLE_CO_OWNER.equals(bo.getRole()))
                .map(bo -> bo.getClient().getId())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID bookingId, UUID clientId) {
        if (bookingId == null || clientId == null) {
            return false;
        }
        return bookingOwnerRepository.findByBooking_IdWithClientOrderBySortOrderAsc(bookingId).stream()
                .anyMatch(bo -> clientId.equals(bo.getClient().getId()));
    }

    @Transactional
    public void syncOwners(Booking booking, List<UUID> coOwnerIds, UUID builderId) {
        UUID bookingId = booking.getId();
        UUID primaryId = booking.getClient().getId();
        List<UUID> normalized =
                (coOwnerIds != null ? coOwnerIds : List.<UUID>of()).stream()
                        .filter(Objects::nonNull)
                        .filter(id -> !id.equals(primaryId))
                        .distinct()
                        .toList();

        for (UUID coId : normalized) {
            clientRepository
                    .findByIdAndBuilder_Id(coId, builderId)
                    .orElseThrow(() -> new IllegalArgumentException("Co-owner client not found: " + coId));
        }

        bookingOwnerRepository.deleteByBooking_IdAndRole(bookingId, BookingOwner.ROLE_CO_OWNER);

        BookingOwner primary =
                bookingOwnerRepository.findByBooking_IdWithClientOrderBySortOrderAsc(bookingId).stream()
                        .filter(bo -> BookingOwner.ROLE_PRIMARY.equals(bo.getRole()))
                        .findFirst()
                        .orElse(null);
        Instant now = Instant.now();
        if (primary == null) {
            primary = new BookingOwner();
            primary.setBooking(booking);
            primary.setClient(booking.getClient());
            primary.setSortOrder(0);
            primary.setRole(BookingOwner.ROLE_PRIMARY);
            primary.setCreatedAt(now);
            bookingOwnerRepository.save(primary);
        } else if (!primary.getClient().getId().equals(primaryId)) {
            primary.setClient(booking.getClient());
            bookingOwnerRepository.save(primary);
        }

        int order = 1;
        for (UUID coId : normalized) {
            Client co = clientRepository.findByIdAndBuilder_Id(coId, builderId).orElseThrow();
            BookingOwner row = new BookingOwner();
            row.setBooking(booking);
            row.setClient(co);
            row.setSortOrder(order++);
            row.setRole(BookingOwner.ROLE_CO_OWNER);
            row.setCreatedAt(now);
            bookingOwnerRepository.save(row);
        }
    }

    public static String formatOwnersDisplay(List<Client> owners) {
        if (owners == null || owners.isEmpty()) {
            return "—";
        }
        Set<String> names = new LinkedHashSet<>();
        for (Client owner : owners) {
            if (owner == null) {
                continue;
            }
            String name = owner.displayName();
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        if (names.isEmpty()) {
            return "—";
        }
        return String.join(" & ", names);
    }
}
