import { expect, type Page } from '@playwright/test';
import { waitForMainPanel } from './projects';

export async function createBookingForFlat(
  page: Page,
  flatId: string,
  clientDisplayName: string,
  bookingDate = '2026-06-15',
): Promise<string> {
  await page.goto(`bookings/new?flatId=${flatId}`, { waitUntil: 'commit' });
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New booking' })).toBeVisible();

  await form.locator('#client\\.id').selectOption({ label: clientDisplayName });
  await form.locator('#flat\\.id').selectOption(flatId);
  await form.locator('#bookingDate').fill(bookingDate);
  await form.locator('#considerationAmt').fill('5000000');

  await Promise.all([
    page.waitForURL(/\/bookings\/[0-9a-f-]+(?:\?|$)/, { timeout: 20_000 }),
    form.getByRole('button', { name: 'Create booking' }).click(),
  ]);

  const detail = await waitForMainPanel(page);
  await expect(detail.locator('.alert-success').filter({ hasText: 'Booking saved' }).first()).toBeVisible();
  const bookingId = page.url().match(/\/bookings\/([0-9a-f-]+)/i)?.[1] ?? '';
  expect(bookingId.length).toBeGreaterThan(0);
  return bookingId;
}

export async function expectBookingInList(page: Page, clientDisplayName: string) {
  await page.goto('bookings', { waitUntil: 'commit' });
  const list = await waitForMainPanel(page);
  await expect(list.getByRole('heading', { name: 'Bookings' })).toBeVisible();
  await expect(list.locator('tbody tr').filter({ hasText: clientDisplayName }).first()).toBeVisible();
}
