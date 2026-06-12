import { expect, type Locator, type Page } from '@playwright/test';
import { openFlatDetailsForFlat, waitForFlatGridReady } from './buildings';
import { clickSidebarLink, openAdminBuildingFlatGrid } from './nav';

const SQFT_PER_SQM = 10.763910416709722;

export type FlatDetailsAreasInput = {
  superBuiltSqft: number;
  carpetSqft: number;
  balconySqm: number;
  price?: number;
};

export type FlatDetailsAreasTestRecord = FlatDetailsAreasInput & {
  flatId: string;
  balconyAreaSqft: number;
};

export const E2E_FLAT_DETAILS_AREAS: FlatDetailsAreasInput = {
  superBuiltSqft: 1090,
  carpetSqft: 800,
  balconySqm: 10,
  price: 12_600_000,
};

export function balconySqftFromSqm(sqm: number): number {
  return Math.round(sqm * SQFT_PER_SQM * 100) / 100;
}

async function expectCardNumericAttribute(card: Locator, attr: string, expected: number) {
  const value = await card.getAttribute(attr);
  expect(value).not.toBeNull();
  expect(Number(value)).toBe(expected);
}

export async function selectAreaUnit(page: Page, pairId: string, unit: 'sqft' | 'sqm') {
  const toggle = page.locator(`#${pairId}-unit`);
  await expect(toggle).toBeVisible();
  const label = unit === 'sqm' ? 'Sq m' : 'Sq ft';
  await toggle.getByRole('button', { name: label, exact: true }).click();
  await expectAreaUnit(page, pairId, unit);
}

export async function expectAreaUnit(page: Page, pairId: string, unit: 'sqft' | 'sqm') {
  const toggle = page.locator(`#${pairId}-unit`);
  await expect(toggle).toHaveAttribute('data-area-unit-value', unit);
  await expect(
    toggle.locator(`.floor21-area-unit-toggle__btn[data-area-unit-value="${unit}"]`),
  ).toHaveClass(/is-active/);
}

export async function fillFlatDetailsAreas(
  page: Page,
  modal: Locator,
  data: FlatDetailsAreasInput,
) {
  await selectAreaUnit(page, 'admin-super-builder-area', 'sqft');
  await modal.locator('#admin-super-builder-area').fill(String(data.superBuiltSqft));
  await selectAreaUnit(page, 'admin-carpet-area', 'sqft');
  await modal.locator('#admin-carpet-area').fill(String(data.carpetSqft));
  await selectAreaUnit(page, 'admin-balcony-area', 'sqm');
  await modal.locator('#admin-balcony-area').fill(data.balconySqm.toFixed(2));
  if (data.price != null) {
    await modal.locator('#admin-price').fill(String(data.price));
  }
}

export async function expectFlatDetailsAreasInModal(
  page: Page,
  modal: Locator,
  data: FlatDetailsAreasInput,
) {
  await expectAreaUnit(page, 'admin-super-builder-area', 'sqft');
  await expect(modal.locator('#admin-super-builder-area')).toHaveValue(String(data.superBuiltSqft));
  await expectAreaUnit(page, 'admin-carpet-area', 'sqft');
  await expect(modal.locator('#admin-carpet-area')).toHaveValue(String(data.carpetSqft));
  await expectAreaUnit(page, 'admin-balcony-area', 'sqm');
  await expect(modal.locator('#admin-balcony-area')).toHaveValue(data.balconySqm.toFixed(2));
  if (data.price != null) {
    const priceValue = await modal.locator('#admin-price').inputValue();
    expect(Number(priceValue)).toBe(data.price);
  }
}

export async function saveFlatDetailsAreas(page: Page, modal: Locator, flatId: string) {
  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/flats/${flatId}/details`) &&
      response.ok(),
    { timeout: 30_000 },
  );
  await modal.locator('#admin-save-btn').click();
  const response = await saveResponse;
  const body = (await response.json()) as {
    areaSqft?: number;
    carpetAreaSqft?: number;
    balconyAreaSqft?: number;
    basePrice?: number;
  };
  return body;
}

export async function expectSavedValuesBanner(modal: Locator) {
  const status = modal.locator('#flat-details-status');
  await expect(status).toBeVisible({ timeout: 10_000 });
  await expect(status).toHaveText('Saved values');
}

export async function expectFlatCardAreaAttributes(
  card: Locator,
  data: FlatDetailsAreasInput,
) {
  const balconySqft = balconySqftFromSqm(data.balconySqm);
  await expectCardNumericAttribute(card, 'data-area', data.superBuiltSqft);
  await expectCardNumericAttribute(card, 'data-carpet-area', data.carpetSqft);
  await expectCardNumericAttribute(card, 'data-balcony-area', balconySqft);
  if (data.price != null) {
    await expectCardNumericAttribute(card, 'data-price', data.price);
  }
  return balconySqft;
}

/**
 * Per-flat flat details: sq ft / sq m toggles, save, "Saved values", grid attrs, navigate away, reopen.
 */
export async function configureAndVerifyFlatDetailsAreas(
  page: Page,
  buildingId: string,
  data: FlatDetailsAreasInput = E2E_FLAT_DETAILS_AREAS,
): Promise<FlatDetailsAreasTestRecord> {
  await openAdminBuildingFlatGrid(page, { buildingId });
  await waitForFlatGridReady(page);

  const sampleFlat = page
    .locator('#flat-grid [data-flat-id][data-parking="false"][data-amenity="false"][data-type="3BHK"]')
    .first();
  await expect(sampleFlat).toBeVisible();
  const flatId = await sampleFlat.getAttribute('data-flat-id');
  expect(flatId).toBeTruthy();

  let modal = await openFlatDetailsForFlat(page, flatId!);
  await expect(modal.locator('#admin-save-btn')).toBeVisible();
  await fillFlatDetailsAreas(page, modal, data);

  const saved = await saveFlatDetailsAreas(page, modal, flatId!);
  expect(Number(saved.areaSqft)).toBe(data.superBuiltSqft);
  expect(Number(saved.carpetAreaSqft)).toBe(data.carpetSqft);
  expect(Number(saved.balconyAreaSqft)).toBe(balconySqftFromSqm(data.balconySqm));
  if (data.price != null) {
    expect(Number(saved.basePrice)).toBe(data.price);
  }

  await expectSavedValuesBanner(modal);
  await expectFlatDetailsAreasInModal(page, modal, data);

  await modal.locator('.btn-close').click();
  await expect(modal).toBeHidden();

  const balconyAreaSqft = await expectFlatCardAreaAttributes(sampleFlat, data);

  await clickSidebarLink(page, 'Dashboard');
  await openAdminBuildingFlatGrid(page, { buildingId });
  await waitForFlatGridReady(page);

  const card = page.locator(`#flat-${flatId}`);
  await expect(card).toBeVisible();
  await expectFlatCardAreaAttributes(card, data);

  modal = await openFlatDetailsForFlat(page, flatId!);
  await expectFlatDetailsAreasInModal(page, modal, data);
  await modal.locator('.btn-close').click();
  await expect(modal).toBeHidden();

  return {
    flatId: flatId!,
    ...data,
    balconyAreaSqft,
  };
}
