import { expect, type Locator, type Page } from '@playwright/test';
import { defaultParkingSlotsPerFloor, waitForFlatGridReady } from './buildings';
import { dismissBootstrapModal } from './modals';
import { waitForMainPanel } from './projects';

export const DEFAULT_PARKING_CAR_SIZE_PERCENT = 180;
export const DEFAULT_SHOP_SIZE_PERCENT = 140;

export type FloorSizeConfigInput = {
  parkingFloor?: number;
  groundShopCount?: number;
  parkingCarSizePercent?: number;
  shopSizePercent?: number;
};

export type FloorSizeConfigTestRecord = {
  parkingFloor: number;
  parkingCarSizePercent: number;
  groundShopCount: number;
  shopSizePercent: number;
};

export const E2E_FLOOR_SIZE_CONFIG: Required<FloorSizeConfigInput> = {
  parkingFloor: 1,
  groundShopCount: 4,
  parkingCarSizePercent: DEFAULT_PARKING_CAR_SIZE_PERCENT,
  shopSizePercent: DEFAULT_SHOP_SIZE_PERCENT,
};

async function isBootstrapModalOpen(page: Page, modalId: string): Promise<boolean> {
  return (await page.locator(`#${modalId}.modal.show`).count()) > 0;
}

/** Dismiss ground floor config modal via footer Close (if still open). */
export async function closeGroundFloorConfigModal(page: Page): Promise<void> {
  await dismissBootstrapModal(page, 'ground-floor-config-modal');
}

export async function closeParkingConfigModalIfOpen(page: Page): Promise<void> {
  await dismissBootstrapModal(page, 'parking-config-modal');
}

async function waitForNoModalBackdrop(page: Page): Promise<void> {
  await expect(page.locator('.modal-backdrop')).toHaveCount(0, { timeout: 10_000 });
}

export async function expectParkingConfigModalWithoutSizeSliders(page: Page): Promise<void> {
  await expect(page.locator('#parking-config-car-size')).toHaveCount(0);
  await expect(page.locator('#parking-config-car-size-value')).toHaveCount(0);
}

export async function expectGroundFloorConfigModalWithoutSizeSliders(page: Page): Promise<void> {
  await expect(page.locator('#ground-floor-config-shop-size')).toHaveCount(0);
  await expect(page.locator('#ground-floor-config-shop-size-value')).toHaveCount(0);
  await expect(page.locator('#ground-floor-config-parking-car-size')).toHaveCount(0);
  await expect(page.locator('#ground-floor-config-parking-car-size-value')).toHaveCount(0);
}

export async function openParkingConfigureModal(page: Page, floorNumber: number): Promise<Locator> {
  await closeParkingConfigModalIfOpen(page);
  await closeGroundFloorConfigModal(page);
  await waitForNoModalBackdrop(page);
  const section = page.locator(`.flat-parking-section[data-floor-number="${floorNumber}"]`);
  await expect(section).toBeVisible();
  await section.scrollIntoViewIfNeeded();
  await expect(section).toHaveAttribute('data-configured', 'true');
  const configure = section.locator('.flat-parking-configure-link');
  await expect(configure).toBeVisible();
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

export async function expectParkingSectionCarSize(section: Locator, expectedPercent: number) {
  await expect(section).toHaveAttribute('data-car-size-percent', String(expectedPercent), {
    timeout: 15_000,
  });
}

export async function openGroundFloorConfigureModal(page: Page): Promise<Locator> {
  await closeGroundFloorConfigModal(page);
  await closeParkingConfigModalIfOpen(page);
  await waitForNoModalBackdrop(page);
  const section = page.locator('#flat-ground-floor-section');
  await expect(section).toBeVisible();
  await section.scrollIntoViewIfNeeded();
  const panel = section.locator('.flat-ground-floor-section__panel');
  const addBtn = section.locator('.ground-floor-add-btn');
  if (await addBtn.isVisible()) {
    await addBtn.scrollIntoViewIfNeeded();
    await addBtn.click();
  } else {
    await panel.locator('.ground-floor-configure-link').scrollIntoViewIfNeeded();
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
  await expect(page.locator('#ground-floor-config-modal')).not.toHaveClass(/show/, {
    timeout: 15_000,
  });
  return response.json();
}

export async function expectGroundFloorPanelShopSize(panel: Locator, expectedPercent: number) {
  await expect(panel).toHaveAttribute('data-shop-size-percent', String(expectedPercent), {
    timeout: 15_000,
  });
}

export async function ensureGroundFloorShopsConfigured(
  page: Page,
  buildingId: string,
  shopCount: number,
): Promise<Locator> {
  const section = page.locator('#flat-ground-floor-section');
  await expect(section).toBeVisible();
  await section.scrollIntoViewIfNeeded();
  const panel = section.locator('.flat-ground-floor-section__panel');
  if ((await panel.getAttribute('data-configured')) === 'true') {
    return panel;
  }

  await openGroundFloorConfigureModal(page);
  await page.locator('#ground-floor-config-shop-count').fill(String(shopCount));
  await page.locator('#ground-floor-config-parking-count').fill('0');
  await page.locator('#ground-floor-config-car-lift-count').fill('0');
  await page.locator('#ground-floor-config-passenger-lift-count').fill('0');
  await page.locator('#ground-floor-config-gate-count').fill('0');
  await saveGroundFloorConfig(page, buildingId);
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
 * Ground floor and parking configure modals no longer expose size sliders.
 * Panel edge-drag resize persists size %; configure save keeps existing values.
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

  const groundPanel = await ensureGroundFloorShopsConfigured(
    page,
    buildingId,
    opts.groundShopCount,
  );
  await openGroundFloorConfigureModal(page);
  await expectGroundFloorConfigModalWithoutSizeSliders(page);
  await expect(page.locator('#ground-floor-config-shop-count')).toHaveValue(
    String(opts.groundShopCount),
  );
  await closeGroundFloorConfigModal(page);
  await expectGroundFloorPanelShopSize(groundPanel, opts.shopSizePercent);

  await openGroundFloorConfigureModal(page);
  await expectGroundFloorConfigModalWithoutSizeSliders(page);
  await page.locator('#ground-floor-config-gate-count').fill('1');
  const groundPlan = (await saveGroundFloorConfig(page, buildingId)) as {
    shopSizePercent?: number;
    parkingCarSizePercent?: number;
  };
  expect(Number(groundPlan.shopSizePercent)).toBe(opts.shopSizePercent);
  await expectGroundFloorPanelShopSize(groundPanel, opts.shopSizePercent);

  await navigateAwayAndBackToFlatGrid(page, buildingId);
  const groundPanelReloaded = page.locator('.flat-ground-floor-section__panel');
  await expect(groundPanelReloaded).toHaveAttribute('data-configured', 'true');
  await expectGroundFloorPanelShopSize(groundPanelReloaded, opts.shopSizePercent);
  await openGroundFloorConfigureModal(page);
  await expectGroundFloorConfigModalWithoutSizeSliders(page);
  await closeGroundFloorConfigModal(page);

  const parkingSection = page.locator(
    `.flat-parking-section[data-floor-number="${parkingFloor}"]`,
  );
  await parkingSection.scrollIntoViewIfNeeded();
  await expect(parkingSection).toBeVisible();
  await expect(parkingSection).toHaveAttribute('data-configured', 'true');
  await expect(parkingSection.locator('.parking-plan__slot')).toHaveCount(slotsPerFloor);

  await openParkingConfigureModal(page, parkingFloor);
  await expect(page.locator('#parking-config-slots')).toHaveValue(String(slotsPerFloor));
  await expectParkingConfigModalWithoutSizeSliders(page);
  const decreasedPlan = await saveParkingConfig(page, buildingId, parkingFloor);
  expect(Number(decreasedPlan.carSizePercent)).toBe(opts.parkingCarSizePercent);
  if (await isBootstrapModalOpen(page, 'parking-config-modal')) {
    await closeParkingConfigModalIfOpen(page);
  }
  await expectParkingSectionCarSize(parkingSection, opts.parkingCarSizePercent);

  await openParkingConfigureModal(page, parkingFloor);
  await expectParkingConfigModalWithoutSizeSliders(page);
  await page.locator('#parking-config-gate-count').fill('2');
  const increasedPlan = await saveParkingConfig(page, buildingId, parkingFloor);
  expect(Number(increasedPlan.carSizePercent)).toBe(opts.parkingCarSizePercent);
  if (await isBootstrapModalOpen(page, 'parking-config-modal')) {
    await closeParkingConfigModalIfOpen(page);
  }
  await expectParkingSectionCarSize(parkingSection, opts.parkingCarSizePercent);

  await navigateAwayAndBackToFlatGrid(page, buildingId);
  const parkingSectionReloaded = page.locator(
    `.flat-parking-section[data-floor-number="${parkingFloor}"]`,
  );
  await parkingSectionReloaded.scrollIntoViewIfNeeded();
  await expectParkingSectionCarSize(parkingSectionReloaded, opts.parkingCarSizePercent);
  await openParkingConfigureModal(page, parkingFloor);
  await expectParkingConfigModalWithoutSizeSliders(page);
  await closeParkingConfigModalIfOpen(page);

  return {
    parkingFloor,
    parkingCarSizePercent: opts.parkingCarSizePercent,
    groundShopCount: opts.groundShopCount,
    shopSizePercent: opts.shopSizePercent,
  };
}
