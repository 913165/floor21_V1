package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floor21.entity.Booking;
import com.floor21.entity.Builder;
import com.floor21.entity.Building;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgreementWordServiceTest {

    @Mock private BookingService bookingService;
    @Mock private BookingOwnerService bookingOwnerService;
    @Mock private BookingPaymentSlabService bookingPaymentSlabService;
    @Mock private ReceiptService receiptService;
    @Mock private UserRepository userRepository;

    @Test
    void generatedAgreementReplacesSampleBuyerAndBookingDetails() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Builder builder = new Builder();
        builder.setId(UUID.randomUUID());
        builder.setCompanyName("Demo Promoter");

        Building building = new Building();
        building.setBuildingName("Demo Heights");
        building.setBuilder(builder);

        Flat flat = new Flat();
        flat.setFlatNumber("1204");
        flat.setFloorNumber(12);
        flat.setCarpetAreaSqft(new BigDecimal("700"));
        flat.setBalconyAreaSqft(new BigDecimal("45"));
        flat.setBuilding(building);

        Client client = new Client();
        client.setFirstName("Asha");
        client.setLastName("Shah");
        client.setPanNumber("ABCDE1234F");
        client.setAddress1("12 Sample Road");
        client.setCity("Navi Mumbai");
        client.setEmail1("asha@example.com");

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setBookingCode("BK-1204");
        booking.setBuilder(builder);
        booking.setFlat(flat);
        booking.setClient(client);
        booking.setAgreementDate(LocalDate.of(2026, 7, 15));
        booking.setConsiderationAmt(new BigDecimal("8400000"));

        when(bookingService.get(bookingId)).thenReturn(booking);
        when(bookingOwnerService.ownersInOrder(booking)).thenReturn(List.of(client));
        when(bookingPaymentSlabService.listLineViews(bookingId)).thenReturn(List.of());
        when(receiptService.listHistoryForBooking(bookingId)).thenReturn(List.of());

        AgreementWordService service =
                new AgreementWordService(
                        bookingService,
                        bookingOwnerService,
                        bookingPaymentSlabService,
                        receiptService,
                        userRepository);

        byte[] result = service.generate(bookingId);
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            text = doc.getParagraphs().stream()
                            .map(p -> p.getText())
                            .reduce("", (a, b) -> a + "\n" + b)
                    + doc.getTables().stream()
                            .map(t -> t.getText())
                            .reduce("", (a, b) -> a + "\n" + b);
        }

        assertThat(text)
                .contains("ASHA SHAH")
                .contains("1204")
                .contains("Demo Heights")
                .doesNotContain("JITIN JOSE")
                .doesNotContain("JENNY JITIN")
                .doesNotContain("KKBKR52025081300715682");
    }
}
