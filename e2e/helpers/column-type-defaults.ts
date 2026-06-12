import { expect, type Page } from '@playwright/test';
import { waitForFlatGridReady, type NewBuildingInput } from './buildings';
import { openAdminBuildingFlatGrid } from './nav';
import { applyDefaultsResultToFlatCards } from './unit-type-defaults';

export type ColumnTypeDefaultsInput = {
  columnNumber: number;
  layoutColumnType?: string;
  areaSqft: number;
  carpetAreaSqft: number;
  balconyAreaSqft: number;
  basePrice: number;
};

export const E2E_COLUMN_1_DEFAULTS: ColumnTypeDefaultsInput = {
  columnNumber: 1,
  layoutColumnType: 'A',
  areaSqft: 880,
  carpetAreaSqft: 680,
  balconyAreaSqft: 48,
  basePrice: 8_200_000,
};

export async function openColumnTypeDefaultsModal(page: Page) {
  const openBtn = page.locator('#grid-configure-column-type-defaults-btn');
  await expect(openBtn).toBeVisible();
  await openBtn.scrollIntoViewIfNeeded();
  await openBtn.click();
  const modal = page.locator('#column-type-defaults-modal');
  await expect(modal).toHaveClass(/show/, { timeout: 15_000 });
  return modal;
}

export async function fillColumnTypeDefaultsModal(page: Page, data: ColumnTypeDefaultsInput) {
  await page.locator('#column-type-defaults-column').selectOption(String(data.columnNumber));
  if (data.layoutColumnType != null) {
    await page.locator('#column-type-defaults-type-label').fill(data.layoutColumnType);
  }
  await page.locator('#column-type-defaults-super-builder-area').fill(String(data.areaSqft));
  await page.locator('#column-type-defaults-carpet-area').fill(String(data.carpetAreaSqft));
  await page.locator('#column-type-defaults-balcony-area').fill(String(data.balconyAreaSqft));
  await page.locator('#column-type-defaults-price').fill(String(data.basePrice));
}

export async function saveColumnTypeDefaultsConfig(page: Page, buildingId: string) {
  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/column-type-defaults`) &&
      !response.url().includes('/apply') &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#column-type-defaults-save').click();
  const response = await saveResponse;
  await expect(page.locator('#column-type-defaults-modal')).not.toHaveClass(/show/, { timeout: 15_000 });
  return (await response.json()) as Record<string, unknown>;
}

export async function applyColumnTypeDefaultsToFlats(page: Page, buildingId: string) {
  const applyResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/column-type-defaults/apply`) &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#column-type-defaults-apply').click();
  const response = await applyResponse;
  return (await response.json()) as {
    defaults?: Record<string, unknown>;
    updatedFlats?: Array<{ id: string }>;
  };
}

export function expectedColumnFlatCount(building: NewBuildingInput, _columnNumber: number): number {
  const residentialFloors = Math.max(
    0,
    (building.totalFloors ?? 9) - (building.parkingFloors ?? 3),
  );
  return residentialFloors;
}

export async function configureAndApplyColumnTypeDefaults(
  page: Page,
  buildingId: string,
  building: NewBuildingInput,
  data: ColumnTypeDefaultsInput,
) {
  await openAdminBuildingFlatGrid(page, { buildingId });
  await waitForFlatGridReady(page);

  const sampleFlat = page
    .locator(
      `#flat-grid [data-flat-id][data-parking="false"][data-amenity="false"][data-column-number="${data.columnNumber}"]`,
    )
    .first();
  await expect(sampleFlat).toBeVisible();
  const areaBeforeSave = await sampleFlat.getAttribute('data-area');

  await openColumnTypeDefaultsModal(page);
  await fillColumnTypeDefaultsModal(page, data);
  const saved = await saveColumnTypeDefaultsConfig(page, buildingId);
  const savedEntry = saved[String(data.columnNumber)] as Record<string, unknown> | undefined;
  expect(savedEntry).toBeTruthy();
  await expect(sampleFlat).toHaveAttribute('data-area', areaBeforeSave ?? '');

  await openColumnTypeDefaultsModal(page);
  const applyResult = await applyColumnTypeDefaultsToFlats(page, buildingId);
  const expectedCount = expectedColumnFlatCount(building, data.columnNumber);
  expect(applyResult.updatedFlats?.length).toBe(expectedCount);
  await applyDefaultsResultToFlatCards(page, applyResult.updatedFlats || []);

  const columnFlats = page.locator(
    `#flat-grid [data-flat-id][data-parking="false"][data-amenity="false"][data-column-number="${data.columnNumber}"]`,
  );
  await expect(columnFlats).toHaveCount(expectedCount);
  for (let i = 0; i < expectedCount; i++) {
    const flat = columnFlats.nth(i);
    await expect(flat).toHaveAttribute('data-area', String(data.areaSqft));
    await expect(flat).toHaveAttribute('data-carpet-area', String(data.carpetAreaSqft));
    await expect(flat).toHaveAttribute('data-balcony-area', String(data.balconyAreaSqft));
    await expect(flat).toHaveAttribute('data-price', String(data.basePrice));
    if (data.layoutColumnType) {
      await expect(flat).toHaveAttribute('data-column-type', data.layoutColumnType);
    }
  }
}
