import { expect, type Page } from '@playwright/test';
import { loadPaymentReceiptsForBooking, loadPaymentScheduleForBooking } from './nav';
import type { PlatformFlowState } from './platform-flow';
import { waitForMainPanel } from './projects';

/** E2E bookings use ₹50,00,000 consideration in helpers/bookings.ts */
export const E2E_BOOKING_CONSIDERATION = 5_000_000;

/** Matches admin import template (RateSlabExcelService). */
export const E2E_MILESTONE_SLAB_PERCENTS = [10, 20, 15, 2.5, 5] as const;

export const E2E_MIN_RECEIPTS_FOR_SLAB_TEST = 5;

function bookingIdForClient(flow: PlatformFlowState, clientDisplayName: string): string {
  const booking = flow.bookings?.find((b) => b.clientDisplayName === clientDisplayName);
  if (!booking?.bookingId) {
    throw new Error(`No booking in flow state for client: ${clientDisplayName}`);
  }
  return booking.bookingId;
}

export function slabAmountForPercent(percent: number): number {
  return Math.round(E2E_BOOKING_CONSIDERATION * (percent / 100));
}

/**
 * Five receipt amounts that waterfall across milestone slabs 1–3 on a ₹50L booking.
 * Aligns with the standard 5-row milestone import template.
 */
export function e2eReceiptAmountsForSlabWaterfall(): number[] {
  const slab1 = slabAmountForPercent(E2E_MILESTONE_SLAB_PERCENTS[0]);
  const slab2 = slabAmountForPercent(E2E_MILESTONE_SLAB_PERCENTS[1]);
  const slab3Half = Math.round(slabAmountForPercent(E2E_MILESTONE_SLAB_PERCENTS[2]) * 0.5);
  const partial1 = Math.round(slab1 * 0.8);
  return [partial1, slab1 - partial1, Math.round(slab2 * 0.5), Math.round(slab2 * 0.5), slab3Half];
}

/** Staggered receipt dates so Days/interest columns are exercised on the schedule. */
export function e2eReceiptDate(receiptIndex: number): string {
  const dates = ['2026-03-28', '2026-04-15', '2026-05-10', '2026-06-01', '2026-06-20', '2026-07-05'];
  return dates[receiptIndex % dates.length];
}

/** @deprecated use e2eReceiptAmountsForSlabWaterfall */
export function logicalReceiptAmount(clientIndex: number): number {
  return e2eReceiptAmountsForSlabWaterfall()[clientIndex % E2E_MIN_RECEIPTS_FOR_SLAB_TEST];
}

export async function createPaymentReceipt(
  page: Page,
  flow: PlatformFlowState,
  clientDisplayName: string,
  amount: number,
  options?: { receiptDate?: string; receiptIndex?: number },
): Promise<string> {
  const bookingId = bookingIdForClient(flow, clientDisplayName);
  const receiptIndex = options?.receiptIndex ?? 0;
  const receiptDate = options?.receiptDate ?? e2eReceiptDate(receiptIndex);

  const loaded = await loadPaymentReceiptsForBooking(page, flow.buildingId, bookingId);
  await loaded.getByRole('button', { name: 'New Payment receipt' }).click();
  const modal = page.locator('#receipt-form-modal');
  await expect(modal).toBeVisible();

  await modal.locator('#amountConsideration').fill(String(amount));
  await modal.locator('#receiptDate').fill(receiptDate);
  await modal.locator('#paymentMode').selectOption('Cheque');
  const chequeNo = `E2E${String(flow.stamp).slice(-6)}${String(receiptIndex + 1).padStart(2, '0')}${Math.floor(Math.random() * 900 + 100)}`;
  await modal.locator('#chequeNo').fill(chequeNo);
  await modal.locator('#bankName').fill('HDFC Bank');

  await Promise.all([
    page.waitForURL(/receipts\?.*bookingId=/, { timeout: 30_000 }),
    modal.getByRole('button', { name: 'Save receipt' }).click(),
  ]);

  const done = await waitForMainPanel(page);
  await expect(
    done.locator('.alert-success').filter({ hasText: /Receipt saved/ }).first(),
  ).toBeVisible();
  return chequeNo;
}

export async function screenshotPaymentSchedule(
  page: Page,
  flow: PlatformFlowState,
  clientDisplayName: string,
  screenshotPath: string,
) {
  const bookingId = bookingIdForClient(flow, clientDisplayName);
  const schedule = await loadPaymentScheduleForBooking(page, flow.buildingId, bookingId);
  await expect(schedule.getByText('Slab payment schedule')).toBeVisible();
  await expect(schedule.locator('.slab-schedule-ledger-table tbody tr').first()).toBeVisible();
  await page.screenshot({ path: screenshotPath, fullPage: true });
}
