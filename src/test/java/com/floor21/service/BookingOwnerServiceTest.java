package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floor21.entity.Booking;
import com.floor21.entity.BookingOwner;
import com.floor21.entity.Client;
import com.floor21.repository.BookingOwnerRepository;
import com.floor21.repository.ClientRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingOwnerServiceTest {

    @Mock private BookingOwnerRepository bookingOwnerRepository;
    @Mock private ClientRepository clientRepository;

    @InjectMocks private BookingOwnerService bookingOwnerService;

    @Test
    void ownersDisplayNameJoinsAllOwners() {
        UUID bookingId = UUID.randomUUID();
        Client primary = client("Client1", "Buyer");
        Client co = client("Client2", "Buyer");
        BookingOwner row1 = ownerRow(primary, BookingOwner.ROLE_PRIMARY, 0);
        BookingOwner row2 = ownerRow(co, BookingOwner.ROLE_CO_OWNER, 1);
        when(bookingOwnerRepository.findByBooking_IdWithClientOrderBySortOrderAsc(bookingId))
                .thenReturn(List.of(row1, row2));

        assertThat(bookingOwnerService.ownersDisplayName(bookingId))
                .isEqualTo("Client1 Buyer & Client2 Buyer");
    }

    @Test
    void formatOwnersDisplayHandlesEmpty() {
        assertThat(BookingOwnerService.formatOwnersDisplay(List.of())).isEqualTo("—");
    }

    private static Client client(String first, String last) {
        Client c = new Client();
        c.setId(UUID.randomUUID());
        c.setFirstName(first);
        c.setLastName(last);
        return c;
    }

    private static BookingOwner ownerRow(Client client, String role, int order) {
        BookingOwner row = new BookingOwner();
        row.setClient(client);
        row.setRole(role);
        row.setSortOrder(order);
        return row;
    }
}
