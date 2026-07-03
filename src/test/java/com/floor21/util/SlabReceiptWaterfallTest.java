package com.floor21.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Receipt;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlabReceiptWaterfallTest {

  @Test
  void buildChequeLabelShowsModeForNeftWithoutChequePrefix() {
    Receipt receipt = new Receipt();
    receipt.setPaymentMode("NEFT");
    receipt.setChequeNo("SBIN126086375649");

    assertThat(SlabReceiptWaterfall.buildChequeLabel(receipt)).isEqualTo("NEFT");
  }

  @Test
  void buildChequeLabelShowsModeForRtgs() {
    Receipt receipt = new Receipt();
    receipt.setPaymentMode("RTGS");
    receipt.setChequeNo("SBIN226010642066");

    assertThat(SlabReceiptWaterfall.buildChequeLabel(receipt)).isEqualTo("RTGS");
  }

  @Test
  void buildChequeLabelKeepsChequeNumberForChequeMode() {
    Receipt receipt = new Receipt();
    receipt.setPaymentMode("Cheque");
    receipt.setChequeNo("823056");

    assertThat(SlabReceiptWaterfall.buildChequeLabel(receipt)).isEqualTo("Chq No:823056");
  }

  @Test
  void buildChequeLabelFallsBackToLegacyChequePrefixWhenModeMissing() {
    Receipt receipt = new Receipt();
    receipt.setChequeNo("097552");

    assertThat(SlabReceiptWaterfall.buildChequeLabel(receipt)).isEqualTo("Chq No:097552");
  }

  @Test
  void allocateExcludesGstFromSlabAmountButShowsGstSeparately() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setId(slabId);
    slab.setAgreedAmount(new BigDecimal("1000000"));
    slab.setExtraAmount(BigDecimal.ZERO);

    Receipt receipt = new Receipt();
    receipt.setId(UUID.randomUUID());
    receipt.setAmountConsideration(new BigDecimal("500000"));
    receipt.setAmountGstComponent(new BigDecimal("30000"));
    receipt.setAmount(new BigDecimal("530000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
            SlabReceiptWaterfall.allocate(List.of(slab), List.of(receipt));

    ReceiptSlabAllocationSlice slice = bySlab.get(slabId).getFirst();
    assertThat(slice.amount()).isEqualByComparingTo("500000");
    assertThat(slice.gstAmount()).isEqualByComparingTo("30000");
  }

  @Test
  void allocateGstOnlyReceiptDoesNotReduceSlabBalanceAmount() {
    UUID slabId = UUID.randomUUID();
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setId(slabId);
    slab.setAgreedAmount(new BigDecimal("1000000"));
    slab.setExtraAmount(BigDecimal.ZERO);

    Receipt receipt = new Receipt();
    receipt.setId(UUID.randomUUID());
    receipt.setAmountConsideration(BigDecimal.ZERO);
    receipt.setAmountGstComponent(new BigDecimal("30000"));
    receipt.setAmount(new BigDecimal("30000"));

    Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
            SlabReceiptWaterfall.allocate(List.of(slab), List.of(receipt));

    ReceiptSlabAllocationSlice slice = bySlab.get(slabId).getFirst();
    assertThat(slice.amount()).isEqualByComparingTo("0");
    assertThat(slice.gstAmount()).isEqualByComparingTo("30000");
  }
}
