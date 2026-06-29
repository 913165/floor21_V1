package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floor21.dto.SlabScheduleLineView;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemandDraftServiceTest {

    private final BookingPaymentSlabService bookingPaymentSlabService =
            mock(BookingPaymentSlabService.class);
    private final DemandDraftService service =
            new DemandDraftService(
                    bookingPaymentSlabService,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DemandLetterDocxFiller());

    @Test
    void buildModelUsesUptoAndCurrentMilestoneRows() {
        BookingPaymentSlab slab1 = slab("Initial booking amount", "1157000");
        BookingPaymentSlab slab2 = slab("Agreement", "2314000");
        List<SlabScheduleLineView> lines =
                List.of(
                        line(slab1, "1157000", "1157000", "0"),
                        line(slab2, "2314000", "400000", "1914000"));
        Booking booking = booking(new BigDecimal("3471000"));

        DemandDraftService.DemandLetterModel model =
                service.buildModel(
                        lines,
                        new DemandDraftService.ReceiptTotals(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        booking);

        assertThat(model.rows()).hasSize(2);
        assertThat(model.rows().get(0).scheduleName())
                .isEqualTo("Upto – Initial booking amount");
        assertThat(model.rows().get(0).instalment()).isEqualByComparingTo("1145430");
        assertThat(model.rows().get(1).scheduleName()).isEqualTo("Agreement");
        assertThat(model.rows().get(1).instalment()).isEqualByComparingTo("2290860");
        assertThat(model.totalInstalment()).isEqualByComparingTo("3436290");
        assertThat(model.payableInstalment()).isEqualByComparingTo("3436290");
    }

    @Test
    void buildModelUsesGrossReceivedInstalmentForSummary() {
        BookingPaymentSlab slab1 = slab("Initial booking amount", "1157000");
        List<SlabScheduleLineView> lines = List.of(line(slab1, "1157000", "0", "1157000"));
        Booking booking = booking(new BigDecimal("1157000"));

        DemandDraftService.DemandLetterModel model =
                service.buildModel(
                        lines,
                        new DemandDraftService.ReceiptTotals(
                                new BigDecimal("1000000"), new BigDecimal("10000"), BigDecimal.ZERO),
                        booking);

        assertThat(model.receivedInstalment()).isEqualByComparingTo("1000000");
        assertThat(model.payableInstalment()).isEqualByComparingTo("145430");
    }

    @Test
    void buildModelWithSingleMilestoneShowsOnlyCurrentRow() {
        BookingPaymentSlab slab1 = slab("Initial booking amount", "1157000");
        List<SlabScheduleLineView> lines = List.of(line(slab1, "1157000", "0", "1157000"));
        Booking booking = booking(new BigDecimal("1157000"));

        DemandDraftService.DemandLetterModel model =
                service.buildModel(
                        lines,
                        new DemandDraftService.ReceiptTotals(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        booking);

        assertThat(model.rows()).hasSize(1);
        assertThat(model.rows().get(0).scheduleName()).isEqualTo("Initial booking amount");
        assertThat(model.rows().get(0).instalment()).isEqualByComparingTo("1145430");
    }

    private Booking booking(BigDecimal consideration) {
        Booking booking = new Booking();
        booking.setConsiderationAmt(consideration);
        when(bookingPaymentSlabService.baseConsideration(booking)).thenReturn(consideration);
        return booking;
    }

    private static BookingPaymentSlab slab(String label, String due) {
        BookingPaymentSlab slab = new BookingPaymentSlab();
        slab.setMilestoneLabel(label);
        slab.setAgreedAmount(new BigDecimal(due));
        slab.setExtraAmount(BigDecimal.ZERO);
        return slab;
    }

    private static SlabScheduleLineView line(
            BookingPaymentSlab slab, String due, String paid, String balance) {
        return new SlabScheduleLineView(
                slab,
                new BigDecimal(due),
                new BigDecimal(paid),
                new BigDecimal(balance),
                List.of());
    }
}
