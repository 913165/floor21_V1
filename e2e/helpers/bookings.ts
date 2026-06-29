import { expect, type Locator, type Page } from '@playwright/test';
import { openBookingsList, openNewBookingForm } from './nav';
import { waitForMainPanel } from './projects';

async function optionValueByLabel(
  select: Locator,
  labelText: string,
  pick: 'first' | 'last' = 'last',
): Promise<string | null> {
  return select.evaluate(
    (el, { labelText, pick }) => {
      const options = Array.from((el as HTMLSelectElement).options);
      const matches = options.filter((o) => (o.textContent ?? '').includes(labelText));
      if (matches.length === 0) {
        return null;
      }
      const option = pick === 'last' ? matches[matches.length - 1] : matches[0];
      return option.value || null;
    },
    { labelText, pick },
  );
}

async function selectClientByDisplayName(form: Locator, clientDisplayName: string) {
  const select = form.locator('[name="client.id"]');
  await expect(select).toBeVisible();

  let value: string | null = null;
  await expect(async () => {
    value = await optionValueByLabel(select, clientDisplayName, 'last');
    expect(value, `Client "${clientDisplayName}" is not in the New booking dropdown`).toBeTruthy();
  }).toPass({ timeout: 15_000 });

  await select.selectOption(value!);
}

async function selectFlat(
  form: Locator,
  flatId: string,
  flatNumber?: string,
) {
  const flatSelect = form.locator('[name="flat.id"]');
  await expect(flatSelect).toBeVisible();

  if ((await flatSelect.locator(`option[value="${flatId}"]`).count()) > 0) {
    await flatSelect.selectOption(flatId);
    return;
  }
  if (flatNumber) {
    const value = await optionValueByLabel(flatSelect, `— ${flatNumber} (`, 'first');
    if (value) {
      await flatSelect.selectOption(value);
      return;
    }
  }
  throw new Error(
    `Flat ${flatId} is not in the New booking dropdown — assign only data-bookable flats and restart the app after code changes.`,
  );
}

export async function createBookingForFlat(
  page: Page,
  flatId: string,
  clientDisplayName: string,
  bookingDate = '2026-06-15',
  flatNumber?: string,
): Promise<{ bookingId: string; bookingCode: string }> {
  const form = await openNewBookingForm(page);

  await selectClientByDisplayName(form, clientDisplayName);
  await selectFlat(form, flatId, flatNumber);
  await form.locator('[name="bookingDate"]').fill(bookingDate);
  await form.locator('[name="considerationAmt"]').fill('5000000');

  await Promise.all([
    page.waitForURL(/\/bookings\/[0-9a-f-]+(?:\?|$)/, { timeout: 20_000 }),
    form.getByRole('button', { name: 'Create booking' }).click(),
  ]);

  const detail = await waitForMainPanel(page);
  await expect(detail.locator('.alert-success').filter({ hasText: 'Booking saved' }).first()).toBeVisible();
  const bookingId = page.url().match(/\/bookings\/([0-9a-f-]+)/i)?.[1] ?? '';
  expect(bookingId.length).toBeGreaterThan(0);
  const bookingCode = (await detail.locator('.client-name').first().textContent())?.trim() ?? '';
  expect(bookingCode.length).toBeGreaterThan(0);
  return { bookingId, bookingCode };
}

export async function expectBookingInList(page: Page, clientDisplayName: string) {
  const list = await openBookingsList(page);
  await expect(list.getByRole('heading', { name: 'Bookings' })).toBeVisible();
  await expect(list.locator('tbody tr').filter({ hasText: clientDisplayName }).first()).toBeVisible();
}
