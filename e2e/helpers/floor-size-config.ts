import { expect, type Locator, type Page } from '@playwright/test';
import { defaultParkingSlotsPerFloor, waitForFlatGridReady } from './buildings';
import { waitForMainPanel } from './projects';

export const DEFAULT_PARKING_CAR_SIZE_PERCENT = 180;
export const DEFAULT_SHOP_SIZE_PERCENT = 140;

export type FloorSizeConfigInput = {
  parkingFloor?: number;
  parkingDecreaseTo?: number;
  parkingIncreaseTo?: number;
  groundShopCount?: number;
  shopDecreaseTo?: number;
  shopIncreaseTo?: number;
};

export type FloorSizeConfigTestRecord = {
  parkingFloor: number;
  parkingCarSizeDecreased: number;
  parkingCarSizeIncreased: number;
  groundShopCount: number;
  shopSizeDecreased: number;
  shopSizeIncreased: number;
};

export const E2E_FLOOR_SIZE_CONFIG: Required<FloorSizeConfigInput> = {
  parkingFloor: 1,
  parkingDecreaseTo: 155,
  parkingIncreaseTo: 170,
  groundShopCount: 4,
  shopDecreaseTo: 115,
  shopIncreaseTo: 125,
};

export function scaleFromSizePercent(percent: number): number {
  return Math.max(0.5, Math.min(2, percent / 100));
}

async function readCssVarFromStyle(style: string | null, varName: string): Promise<number> {
  expect(style).toBeTruthy();
  const match = style!.match(new RegExp(`${varName}:\\s*([0-9.]+)`));
  expect(match, `Expected ${varName} in style: ${style}`).toBeTruthy();
  return Number(match![1]);
}

export async function expectParkingGridCarScale(
  section: Locator,
  carSizePercent: number,
): Promise<void> {
  const sheet = section.locator('.parking-plan__sheet').first();
  await expect(sheet).toBeVisible({ timeout: 15_000 });
  const style = await sheet.getAttribute('style');
  const scale = await readCssVarFromStyle(style, '--parking-car-scale');
  expect(scale).toBeCloseTo(scaleFromSizePercent(carSizePercent), 5);
}

export async function expectGroundFloorShopGridScale(
  panel: Locator,
  shopSizePercent: number,
): Promise<void> {
  const sheet = panel.locator('.shop-plan__sheet').first();
  await expect(sheet).toBeVisible({ timeout: 15_000 });
  const style = await sheet.getAttribute('style');
  const scale = await readCssVarFromStyle(style, '--shop-car-scale');
  expect(scale).toBeCloseTo(scaleFromSizePercent(shopSizePercent), 5);
}

export async function setRangeSlider(page: Page, selector: string, value: number) {
  const slider = page.locator(selector);
  await expect(slider).toBeVisible();
  await slider.scrollIntoViewIfNeeded();
  await slider.fill(String(value));
  await expect(slider).toHaveValue(String(value));
}

export async function openParkingConfigureModal(page: Page, floorNumber: number): Promise<Locator> {
  const section = page.locator(`.flat-parking-section[data-floor-number="${floorNumber}"]`);
  await expect(section).toBeVisible();
  await expect(section).toHaveAttribute('data-configured', 'true');
  const configure = section.locator('.flat-parking-configure-link');
  await configure.scrollIntoViewIfNeeded();
  await configure.click();
  const modal = page.locator('#parking-config-modal');
  await expect(modal).toHaveClass(/show/, { timeout: 15_000 });
  return modal;
}

export async function saveParkingConfig(page: Page, buildingId: string, floorNumber: number) {
  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/flats/floor/${floorNumber}/parking-config`) &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#parking-config-save').click();
  const response = await saveResponse;
  const plan = (await response.json()) as { carSizePercent?: number };
  return plan;
}

export async function expectParkingCarSizeInModal(page: Page, expectedPercent: number) {
  await expect(page.locator('#parking-config-car-size')).toHaveValue(String(expectedPercent));
  await expect(page.locator('#parking-config-car-size-value')).toHaveText(String(expectedPercent));
}

export async function expectParkingSectionCarSize(section: Locator, expectedPercent: number) {
  await expect(section).toHaveAttribute('data-car-size-percent', String(expectedPercent), {
    timeout: 15_000,
  });
  await expectParkingGridCarScale(section, expectedPercent);
}

export async function openGroundFloorConfigureModal(page: Page): Promise<Locator> {
  const section = page.locator('#flat-ground-floor-section');
  await expect(section).toBeVisible();
  const panel = section.locator('.flat-ground-floor-section__panel');
  const addBtn = section.locator('.ground-floor-add-btn');
  if (await addBtn.isVisible()) {
    await addBtn.click();
  } else {
    await panel.locator('.ground-floor-configure-link').click();
  }
  const modal = page.locator('#ground-floor-config-modal');
  await expect(modal).toHaveClass(/show/, { timeout: 15_000 });
  return modal;
}

export async function saveGroundFloorConfig(page: Page, buildingId: string) {
  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/buildings/${buildingId}/ground-floor-config`) &&
      response.ok(),
    { timeout: 60_000 },
  );
  await page.locator('#ground-floor-config-save').click();
  const response = await saveResponse;
  return response.json();
}

export async function expectGroundFloorSavedValues(modal: Locator) {
  const status = modal.locator('#ground-floor-config-status');
  await expect(status).toBeVisible({ timeout: 15_000 });
  await expect(status).toHaveText('Saved values');
}

export async function expectGroundFloorShopSizeInModal(page: Page, expectedPercent: number) {
  await expect(page.locator('#ground-floor-config-shop-size')).toHaveValue(String(expectedPercent));
  await expect(page.locator('#ground-floor-config-shop-size-value')).toHaveText(
    String(expectedPercent),
  );
}

export async function expectGroundFloorPanelShopSize(panel: Locator, expectedPercent: number) {
  await expect(panel).toHaveAttribute('data-shop-size-percent', String(expectedPercent), {
    timeout: 15_000,
  });
  await expectGroundFloorShopGridScale(panel, expectedPercent);
}

export async function ensureGroundFloorShopsConfigured(
  page: Page,
  buildingId: string,
  shopCount: number,
): Promise<Locator> {
  const section = page.locator('#flat-ground-floor-section');
  await expect(section).toBeVisible();
  const panel = section.locator('.flat-ground-floor-section__panel');
  if ((await panel.getAttribute('data-configured')) === 'true') {
    return panel;
  }

  const modal = await openGroundFloorConfigureModal(page);
  await page.locator('#ground-floor-config-shop-count').fill(String(shopCount));
  await page.locator('#ground-floor-config-parking-count').fill('0');
  await page.locator('#ground-floor-config-car-lift-count').fill('0');
  await page.locator('#ground-floor-config-passenger-lift-count').fill('0');
  await page.locator('#ground-floor-config-gate-count').fill('0');
  await saveGroundFloorConfig(page, buildingId);
  await expectGroundFloorSavedValues(modal);
  await expect(panel).toHaveAttribute('data-configured', 'true', { timeout: 15_000 });
  await expect(panel.locator('.shop-plan__slot--shop')).toHaveCount(shopCount, { timeout: 30_000 });
  return panel;
}

async function navigateAwayAndBackToFlatGrid(page: Page, buildingId: string) {
  await page.goto('dashboard', { waitUntil: 'commit' });
  await waitForMainPanel(page);
  await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
  await waitForMainPanel(page);
  await waitForFlatGridReady(page);
}

/**
 * Parking floor: decrease car size, save, reopen + reload grid, then increase and verify again.
 * Ground floor shops: configure if needed, decrease shop size, save, reopen + reload, then increase.
 */
export async function configureAndVerifyFloorSizes(
  page: Page,
  buildingId: string,
  input: FloorSizeConfigInput = {},
): Promise<FloorSizeConfigTestRecord> {
  const opts = { ...E2E_FLOOR_SIZE_CONFIG, ...input };
  const parkingFloor = opts.parkingFloor;
  const slotsPerFloor = defaultParkingSlotsPerFloor({
    totalFloors: 9,
    parkingFloors: 3,
    flatsPerFloor: 5,
  });

  await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
  await waitForMainPanel(page);
  await waitForFlatGridReady(page);

  const parkingSection = page.locator(
    `.flat-parking-section[data-floor-number="${parkingFloor}"]`,
  );
  await expect(parkingSection).toBeVisible();
  await expect(parkingSection).toHaveAttribute('data-configured', 'true');
  await expect(parkingSection.locator('.parking-plan__slot')).toHaveCount(slotsPerFloor);

  let parkingModal = await openParkingConfigureModal(page, parkingFloor);
  await expect(page.locator('#parking-config-slots')).toHaveValue(String(slotsPerFloor));
  await setRangeSlider(page, '#parking-config-car-size', opts.parkingDecreaseTo);
  await expectParkingCarSizeInModal(page, opts.parkingDecreaseTo);
  const decreasedPlan = await saveParkingConfig(page, buildingId, parkingFloor);
  expect(Number(decreasedPlan.carSizePercent)).toBe(opts.parkingDecreaseTo);
  await expect(parkingModal).not.toHaveClass(/show/, { timeout: 30_000 });
  await expectParkingSectionCarSize(parkingSection, opts.parkingDecreaseTo);

  parkingModal = await openParkingConfigureModal(page, parkingFloor);
  await expectParkingCarSizeInModal(page, opts.parkingDecreaseTo);
  await parkingModal.locator('[data-bs-dismiss="modal"]').click();
  await expect(parkingModal).not.toHaveClass(/show/);

  await navigateAwayAndBackToFlatGrid(page, buildingId);
  const parkingSectionReloaded = page.locator(
    `.flat-parking-section[data-floor-number="${parkingFloor}"]`,
  );
  await expectParkingSectionCarSize(parkingSectionReloaded, opts.parkingDecreaseTo);
  parkingModal = await openParkingConfigureModal(page, parkingFloor);
  await expectParkingCarSizeInModal(page, opts.parkingDecreaseTo);

  await setRangeSlider(page, '#parking-config-car-size', opts.parkingIncreaseTo);
  const increasedPlan = await saveParkingConfig(page, buildingId, parkingFloor);
  expect(Number(increasedPlan.carSizePercent)).toBe(opts.parkingIncreaseTo);
  await expect(parkingModal).not.toHaveClass(/show/, { timeout: 30_000 });
  await expectParkingSectionCarSize(parkingSectionReloaded, opts.parkingIncreaseTo);

  const groundPanel = await ensureGroundFloorShopsConfigured(
    page,
    buildingId,
    opts.groundShopCount,
  );
  let groundModal = await openGroundFloorConfigureModal(page);
  await setRangeSlider(page, '#ground-floor-config-shop-size', opts.shopDecreaseTo);
  await expectGroundFloorShopSizeInModal(page, opts.shopDecreaseTo);
  await saveGroundFloorConfig(page, buildingId);
  await expectGroundFloorSavedValues(groundModal);
  await expectGroundFloorPanelShopSize(groundPanel, opts.shopDecreaseTo);

  groundModal = await openGroundFloorConfigureModal(page);
  await expectGroundFloorShopSizeInModal(page, opts.shopDecreaseTo);
  await groundModal.locator('[data-bs-dismiss="modal"]').click();
  await expect(groundModal).not.toHaveClass(/show/);

  await navigateAwayAndBackToFlatGrid(page, buildingId);
  const groundPanelReloaded = page.locator('.flat-ground-floor-section__panel');
  await expect(groundPanelReloaded).toHaveAttribute('data-configured', 'true');
  await expectGroundFloorPanelShopSize(groundPanelReloaded, opts.shopDecreaseTo);
  groundModal = await openGroundFloorConfigureModal(page);
  await expectGroundFloorShopSizeInModal(page, opts.shopDecreaseTo);

  await setRangeSlider(page, '#ground-floor-config-shop-size', opts.shopIncreaseTo);
  await saveGroundFloorConfig(page, buildingId);
  await expectGroundFloorSavedValues(groundModal);
  await expectGroundFloorPanelShopSize(groundPanelReloaded, opts.shopIncreaseTo);

  groundModal = await openGroundFloorConfigureModal(page);
  await expectGroundFloorShopSizeInModal(page, opts.shopIncreaseTo);
  await groundModal.locator('.btn-close').click();
  await expect(groundModal).not.toHaveClass(/show/);

  return {
    parkingFloor,
    parkingCarSizeDecreased: opts.parkingDecreaseTo,
    parkingCarSizeIncreased: opts.parkingIncreaseTo,
    groundShopCount: opts.groundShopCount,
    shopSizeDecreased: opts.shopDecreaseTo,
    shopSizeIncreased: opts.shopIncreaseTo,
  };
}
