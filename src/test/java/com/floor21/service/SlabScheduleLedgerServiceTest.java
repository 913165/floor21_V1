package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.dto.SlabLedgerRowType;
import com.floor21.dto.SlabScheduleLedgerRow;
import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlabScheduleLedgerServiceTest {

  private final SlabScheduleLedgerService service = new SlabScheduleLedgerService(null, null);

  @Test
  void receiptRowShowsFullChequeAmountOnFirstSlabWhenPaymentOverflows() {
    UUID slab1Id = UUID.randomUUID();
    UUID slab2Id = UUID.randomUUID();
    UUID receipt4Id = UUID.randomUUID();

    BookingPaymentSlab slab1 = slab(slab1Id, "Initial booking amount", new BigDecimal("1157000"));
    BookingPaymentSlab slab2 = slab(slab2Id, "Agreement", new BigDecimal("2314000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slab1Id,
        List.of(
            slice(slab1Id, UUID.randomUUID(), new BigDecimal("400000"), "Chq No:097551"),
            slice(slab1Id, UUID.randomUUID(), new BigDecimal("400000"), "Chq No:097552"),
            slice(slab1Id, UUID.randomUUID(), new BigDecimal("300000"), "Chq No:097553"),
            slice(slab1Id, receipt4Id, new BigDecimal("57000"), "Chq No:BKIDN24244146600")));
    bySlab.put(slab2Id, List.of(slice(slab2Id, receipt4Id, new BigDecimal("93000"), "Chq No:BKIDN24244146600")));

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab1, slab2), bySlab, new BigDecimal("15"), null);

    List<SlabScheduleLedgerRow> slab1Receipts =
        rows.stream()
            .filter(
                r ->
                    r.rowType() == SlabLedgerRowType.RECEIPT
                            && r.receiptId() != null
                            && r.receiptId().equals(receipt4Id)
                            && r.receiptAmount().compareTo(new BigDecimal("100000")) > 0)
            .toList();

    assertThat(slab1Receipts).hasSize(1);
    assertThat(slab1Receipts.get(0).receiptAmount()).isEqualByComparingTo("150000");
    assertThat(slab1Receipts.get(0).balance()).isEqualByComparingTo("-93000");
    assertThat(slab1Receipts.get(0).days()).isEqualTo(29);
    assertThat(slab1Receipts.get(0).interest()).isEqualByComparingTo("679");
    assertThat(slab1Receipts.get(0).info())
        .isEqualTo("Rs.679 as interest for 29 days for Rs.57000 @ 15 %");
  }

  @Test
  void receiptRowInterestUsesSliceAmountAndDueDateToPaymentDate() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Initial booking amount", new BigDecimal("1157000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slabId,
        List.of(slice(slabId, UUID.randomUUID(), new BigDecimal("400000"), "Chq No:097551")));

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), bySlab, new BigDecimal("15"), null);

    SlabScheduleLedgerRow receipt =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.RECEIPT)
            .findFirst()
            .orElseThrow();

    assertThat(receipt.days()).isEqualTo(29);
    assertThat(receipt.interest()).isEqualByComparingTo("4767");
    assertThat(receipt.info())
        .isEqualTo("Rs.4767 as interest for 29 days for Rs.400000 @ 15 %");
  }

  @Test
  void summarizeLedgerIncludesReceiptAndTodayInterest() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Initial booking amount", new BigDecimal("1157000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slabId,
        List.of(slice(slabId, UUID.randomUUID(), new BigDecimal("400000"), "Chq No:097551")));

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), bySlab, new BigDecimal("15"), null);

    var summary = service.summarizeLedger(rows);

    BigDecimal receiptInterest =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.RECEIPT)
            .map(SlabScheduleLedgerRow::interest)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal todayInterest =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.TODAY)
            .map(SlabScheduleLedgerRow::interest)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(summary.totalInterest()).isEqualByComparingTo(receiptInterest.add(todayInterest));
  }

  @Test
  void todayRowAppearsWhenDueDatePassedEvenWithoutReceipts() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Plinth work", new BigDecimal("1735500"));
    slab.setDueDate(LocalDate.now().minusDays(30));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(slabId, List.of());

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), bySlab, new BigDecimal("15"), null);

    assertThat(
            rows.stream()
                .filter(r -> r.rowType() == SlabLedgerRowType.TODAY)
                .findFirst()
                .orElseThrow()
                .receiptAmount())
        .isEqualByComparingTo("1735500");
  }

  @Test
  void slabsThroughLastDueDateStopsAtFirstUndated() {
    BookingPaymentSlab slab1 = slab(UUID.randomUUID(), "Slab 1", new BigDecimal("100"));
    slab1.setDueDate(LocalDate.of(2026, 3, 28));
    BookingPaymentSlab slab2 = slab(UUID.randomUUID(), "Slab 2", new BigDecimal("200"));
    slab2.setDueDate(LocalDate.of(2026, 5, 9));
    BookingPaymentSlab slab3 = slab(UUID.randomUUID(), "Slab 3", new BigDecimal("300"));
    slab3.setDueDate(null);

    List<BookingPaymentSlab> dated =
        BookingPaymentSlabService.slabsThroughLastDueDate(List.of(slab1, slab2, slab3));

    assertThat(dated).containsExactly(slab1, slab2);

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(dated, Map.of(), new BigDecimal("15"), null);
    long milestoneRows =
        rows.stream().filter(r -> r.rowType() == SlabLedgerRowType.SLAB_TOTAL).count();
    assertThat(milestoneRows).isEqualTo(2);
  }

  @Test
  void interestStartsFromBookingDateWhenSlabDueDateIsEarlier() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Initial booking amount", new BigDecimal("2200000"));
    slab.setDueDate(LocalDate.of(2026, 2, 3));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slabId,
        List.of(
            new ReceiptSlabAllocationSlice(
                slabId,
                UUID.randomUUID(),
                LocalDate.of(2026, 3, 18),
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                "NEFT",
                null,
                "NEFT"),
            new ReceiptSlabAllocationSlice(
                slabId,
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 27),
                new BigDecimal("715000"),
                BigDecimal.ZERO,
                "NEFT",
                null,
                "NEFT")));

    LocalDate bookingDate = LocalDate.of(2026, 6, 18);
    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), bySlab, new BigDecimal("15"), bookingDate);

    List<SlabScheduleLedgerRow> receiptRows =
        rows.stream().filter(r -> r.rowType() == SlabLedgerRowType.RECEIPT).toList();

    assertThat(receiptRows.get(0).days()).isNull();
    assertThat(receiptRows.get(0).interest()).isNull();

    assertThat(receiptRows.get(1).days()).isEqualTo(9);
    assertThat(receiptRows.get(1).interest()).isEqualByComparingTo("2645");
  }

  @Test
  void slabTotalRowShowsBookingDateWhenSlabDueDateIsEarlier() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Initial booking amount", new BigDecimal("2200000"));
    slab.setDueDate(LocalDate.of(2026, 2, 3));

    LocalDate bookingDate = LocalDate.of(2026, 6, 18);
    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), Map.of(), new BigDecimal("15"), bookingDate);

    SlabScheduleLedgerRow slabRow =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.SLAB_TOTAL)
            .findFirst()
            .orElseThrow();

    assertThat(slabRow.date()).isEqualTo(bookingDate);
  }

  @Test
  void receiptRowShowsGstOnFirstSlabAndSliceGstOnForwardedSlab() {
    UUID slab1Id = UUID.randomUUID();
    UUID slab2Id = UUID.randomUUID();
    UUID receiptId = UUID.randomUUID();

    BookingPaymentSlab slab1 = slab(slab1Id, "Initial booking amount", new BigDecimal("100000"));
    BookingPaymentSlab slab2 = slab(slab2Id, "Agreement", new BigDecimal("200000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slab1Id,
        List.of(slice(slab1Id, receiptId, new BigDecimal("80000"), new BigDecimal("12000"), "Chq No:1")));
    bySlab.put(
        slab2Id,
        List.of(slice(slab2Id, receiptId, new BigDecimal("20000"), new BigDecimal("3000"), "Chq No:1")));

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab1, slab2), bySlab, new BigDecimal("15"), null);

    SlabScheduleLedgerRow first =
        rows.stream()
            .filter(
                r ->
                    r.rowType() == SlabLedgerRowType.RECEIPT
                            && slab1Id.equals(bySlab.get(slab1Id).get(0).slabId()))
            .findFirst()
            .orElseThrow();
    SlabScheduleLedgerRow forwarded =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.RECEIPT && receiptId.equals(r.receiptId()))
            .skip(1)
            .findFirst()
            .orElseThrow();

    assertThat(first.gstAmount()).isEqualByComparingTo("15000");
    assertThat(forwarded.gstAmount()).isEqualByComparingTo("3000");

    var summary = service.summarizeLedger(rows);
    assertThat(summary.totalGst()).isEqualByComparingTo("15000");
  }

  @Test
  void gstOnlyReceiptShowsInGstColumnNotReceiptColumnOrBalance() {
    UUID slabId = UUID.randomUUID();
    UUID receiptId = UUID.randomUUID();
    BookingPaymentSlab slab = slab(slabId, "Initial booking amount", new BigDecimal("1000000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
    bySlab.put(
        slabId,
        List.of(slice(slabId, receiptId, BigDecimal.ZERO, new BigDecimal("30000"), "NEFT")));

    List<SlabScheduleLedgerRow> rows =
        service.buildLedgerRows(List.of(slab), bySlab, new BigDecimal("15"), null);

    SlabScheduleLedgerRow receiptRow =
        rows.stream()
            .filter(r -> r.rowType() == SlabLedgerRowType.RECEIPT)
            .findFirst()
            .orElseThrow();

    assertThat(receiptRow.receiptAmount()).isEqualByComparingTo("0");
    assertThat(receiptRow.gstAmount()).isEqualByComparingTo("30000");
    assertThat(receiptRow.balance()).isEqualByComparingTo("1000000");
  }

  private static BookingPaymentSlab slab(UUID id, String label, BigDecimal agreed) {
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setId(id);
    slab.setMilestoneLabel(label);
    slab.setAgreedAmount(agreed);
    slab.setExtraAmount(BigDecimal.ZERO);
    slab.setDueDate(LocalDate.of(2026, 3, 28));
    return slab;
  }

  private static ReceiptSlabAllocationSlice slice(
      UUID slabId, UUID receiptId, BigDecimal amount, String chequeLabel) {
    return slice(slabId, receiptId, amount, BigDecimal.ZERO, chequeLabel);
  }

  private static ReceiptSlabAllocationSlice slice(
      UUID slabId,
      UUID receiptId,
      BigDecimal amount,
      BigDecimal gstAmount,
      String chequeLabel) {
    return new ReceiptSlabAllocationSlice(
        slabId,
        receiptId,
        LocalDate.of(2026, 4, 26),
        amount,
        gstAmount,
        chequeLabel,
        null,
        chequeLabel);
  }
}
