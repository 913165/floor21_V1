import { expect, type Page } from '@playwright/test';
import { waitForFlatGridReady } from './buildings';
import { openAdminBuildingFlatGrid } from './nav';

export type UnitTypeDefaultsInput = {
  bhkType: string;
  areaSqft: number;
  carpetAreaSqft: number;
  balconyAreaSqft: number;
  basePrice: number;
};

export const E2E_2BHK_UNIT_DEFAULTS: UnitTypeDefaultsInput = {
  bhkType: '2BHK',
  areaSqft: 920,
  carpetAreaSqft: 710,
  balconyAreaSqft: 55,
  basePrice: 8_500_000,
};

export async function applyDefaultsResultToFlatCards(
  page: Page,
  updatedFlats: Array<{
    id: string;
    areaSqft?: number | string;
    carpetAreaSqft?: number | string;
    balconyAreaSqft?: number | string;
    basePrice?: number | string;
    gridTypeLabel?: string;
    layoutColumnType?: string;
  }>,
) {
  if (!updatedFlats.length) return;
  for (const flat of updatedFlats) {
    const card = page.locator('#flat-' + flat.id);
    await expect(card).toBeVisible();
    if (flat.areaSqft != null) await expect(card).toHaveAttribute('data-area', String(flat.areaSqft));
    if (flat.carpetAreaSqft != null) {
      await expect(card).toHaveAttribute('data-carpet-area', String(flat.carpetAreaSqft));
    }
    if (flat.balconyAreaSqft != null) {
      await expect(card).toHaveAttribute('data-balcony-area', String(flat.balconyAreaSqft));
    }
    if (flat.basePrice != null) await expect(card).toHaveAttribute('data-price', String(flat.basePrice));
  }
}

export async function openUnitTypeDefaultsModal(page: Page) {
  const openBtn = page.locator('#grid-configure-type-defaults-btn');
  await expect(openBtn).toBeVisible();
  await openBtn.scrollIntoViewIfNeeded();
  await openBtn.click();
  const modal = page.locator('#unit-type-defaults-modal');
  await expect(modal).toHaveClass(/show/, { timeout: 15_000 });
  return modal;
}

export async function fillUnitTypeDefaultsModal(page: Page, data: UnitTypeDefaultsInput) {
  await page.locator('#unit-type-defaults-bhk').selectOption(data.bhkType);
  await page.locator('#unit-type-defaults-super-builder-area').fill(String(data.areaSqft));
  await page.locator('#unit-type-defaults-carpet-area').fill(String(data.carpetAreaSqft));
  await page.locator('#unit-type-defaults-balcony-area').fill(String(data.balconyAreaSqft));
  await page.locator('#unit-type-defaults-price').fill(String(data.basePrice));
}

export async function saveUnitTypeDefaultsConfig(page: Page, buildingId: string) {
  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/unit-type-defaults`) &&
      !response.url().includes('/apply') &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#unit-type-defaults-save').click();
  const response = await saveResponse;
  const body = (await response.json()) as Record<string, unknown>;
  await expect(page.locator('#unit-type-defaults-modal')).not.toHaveClass(/show/, { timeout: 15_000 });
  return body;
}

export async function applyUnitTypeDefaultsToFlats(page: Page, buildingId: string) {
  const applyResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/unit-type-defaults/apply`) &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#unit-type-defaults-apply').click();
  const response = await applyResponse;
  return (await response.json()) as {
    defaults?: Record<string, unknown>;
    updatedFlats?: Array<{ id: string; bhkType?: string }>;
  };
}

function residentialFlatLocator(page: Page, bhkType: string) {
  return page.locator(
    `#flat-grid [data-flat-id][data-parking="false"][data-amenity="false"][data-type="${bhkType}"]`,
  );
}

export async function expectUnitTypeDefaultsOnAllFlats(
  page: Page,
  data: UnitTypeDefaultsInput,
  expectedCount: number,
) {
  const flats = residentialFlatLocator(page, data.bhkType);
  await expect(flats).toHaveCount(expectedCount);
  for (let i = 0; i < expectedCount; i++) {
    const flat = flats.nth(i);
    await expect(flat).toHaveAttribute('data-area', String(data.areaSqft));
    await expect(flat).toHaveAttribute('data-carpet-area', String(data.carpetAreaSqft));
    await expect(flat).toHaveAttribute('data-balcony-area', String(data.balconyAreaSqft));
    await expect(flat).toHaveAttribute('data-price', String(data.basePrice));
  }
}

export async function configureAndApplyUnitTypeDefaults(
  page: Page,
  buildingId: string,
  data: UnitTypeDefaultsInput,
  expectedFlatCount: number,
) {
  await openAdminBuildingFlatGrid(page, { buildingId });
  await waitForFlatGridReady(page);

  const sampleFlat = residentialFlatLocator(page, data.bhkType).first();
  await expect(sampleFlat).toBeVisible();
  const areaBeforeSave = await sampleFlat.getAttribute('data-area');

  await openUnitTypeDefaultsModal(page);
  await fillUnitTypeDefaultsModal(page, data);

  const saved = await saveUnitTypeDefaultsConfig(page, buildingId);
  const savedEntry = saved[data.bhkType] as Record<string, unknown> | undefined;
  expect(savedEntry).toBeTruthy();
  expect(Number(savedEntry?.areaSqft)).toBe(data.areaSqft);
  expect(Number(savedEntry?.carpetAreaSqft)).toBe(data.carpetAreaSqft);
  expect(Number(savedEntry?.balconyAreaSqft)).toBe(data.balconyAreaSqft);
  expect(Number(savedEntry?.basePrice)).toBe(data.basePrice);

  await expect(sampleFlat).toHaveAttribute('data-area', areaBeforeSave ?? '');

  await openUnitTypeDefaultsModal(page);
  await expect(page.locator('#unit-type-defaults-bhk')).toHaveValue(data.bhkType);
  await expect(page.locator('#unit-type-defaults-super-builder-area')).toHaveValue(String(data.areaSqft));
  await expect(page.locator('#unit-type-defaults-carpet-area')).toHaveValue(String(data.carpetAreaSqft));
  await expect(page.locator('#unit-type-defaults-balcony-area')).toHaveValue(String(data.balconyAreaSqft));
  await expect(page.locator('#unit-type-defaults-price')).toHaveValue(String(data.basePrice));

  const applyResult = await applyUnitTypeDefaultsToFlats(page, buildingId);
  expect(applyResult.updatedFlats?.length).toBe(expectedFlatCount);

  await expectUnitTypeDefaultsOnAllFlats(page, data, expectedFlatCount);
}
