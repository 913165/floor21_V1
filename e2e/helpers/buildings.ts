import { expect, type Locator, type Page } from '@playwright/test';
import { waitForMainPanel } from './projects';

export type NewBuildingInput = {
  name: string;
  totalFloors?: number;
  parkingFloors?: number;
  flatsPerFloor?: number;
  twoBhkPerFloor?: number;
  city?: string;
  address?: string;
};

export function expectedResidentialFlatCount(data: NewBuildingInput): number {
  const totalFloors = data.totalFloors ?? 5;
  const parkingFloors = data.parkingFloors ?? 0;
  const flatsPerFloor = data.flatsPerFloor ?? 4;
  return Math.max(0, totalFloors - parkingFloors) * flatsPerFloor;
}

/** Default parking fixtures configured on each parking floor during E2E building setup. */
export const DEFAULT_PARKING_FIXTURES = {
  carLiftCount: 2,
  passengerLiftCount: 1,
  gateCount: 0,
} as const;

/** Default parking slots per floor: one more than residential flats spread across parking floors. */
export function defaultParkingSlotsPerFloor(data: NewBuildingInput): number {
  const parkingFloors = data.parkingFloors ?? 0;
  if (parkingFloors <= 0) {
    return 0;
  }
  const residential = expectedResidentialFlatCount(data);
  return Math.ceil(residential / parkingFloors) + 1;
}

export function expectedParkingFlatCount(data: NewBuildingInput): number {
  const parkingFloors = data.parkingFloors ?? 0;
  if (parkingFloors <= 0) {
    return 0;
  }
  return parkingFloors * defaultParkingSlotsPerFloor(data);
}

export function sampleBuildingData(index: number): NewBuildingInput {
  const stamp = Date.now();
  return {
    name: `E2E Tower ${stamp}-${index}`,
    totalFloors: 5,
    parkingFloors: 0,
    flatsPerFloor: 4,
    twoBhkPerFloor: 4,
    city: 'Mumbai',
    address: `E2E Building Address ${index}, Andheri`,
  };
}

export async function openAllBuildingsList(page: Page): Promise<Locator> {
  await page.goto('admin/buildings', { waitUntil: 'commit' });
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'All buildings' })).toBeVisible();
  return main;
}

export async function openNewBuildingForm(page: Page, projectId: string): Promise<Locator> {
  await page.goto(`admin/buildings/new?builderId=${projectId}`, { waitUntil: 'commit' });
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: /Add building layout/ })).toBeVisible();
  await expect(main.locator('#admin-builder-id')).toHaveValue(projectId);
  return main;
}

export async function fillNewBuildingForm(main: Locator, data: NewBuildingInput) {
  await main.locator('#buildingName').fill(data.name);
  await main.locator('#totalFloors').fill(String(data.totalFloors ?? 5));
  await main.locator('#parkingFloors').fill(String(data.parkingFloors ?? 0));
  await main.locator('#flatsPerFloor').fill(String(data.flatsPerFloor ?? 4));
  const twoBhk = main.locator('input[name="bhkPerFloor[\'2BHK\']"]');
  await twoBhk.fill(String(data.twoBhkPerFloor ?? data.flatsPerFloor ?? 4));

  if (data.city) {
    await main.locator('#city').fill(data.city);
  }
  if (data.address) {
    await main.locator('#address').fill(data.address);
  }
}

export async function submitNewBuildingForm(main: Locator, page: Page) {
  const submit = main.getByRole('button', { name: 'Generate building layout' });
  await submit.scrollIntoViewIfNeeded();
  await Promise.all([
    page.waitForURL(/\/buildings\/[0-9a-f-]+\/flats/i, { timeout: 90_000 }),
    submit.click(),
  ]);
}

/** flat-grid.js attaches click handlers after data-f21-init; Bootstrap modals need bootstrap.Modal. */
export async function waitForFlatGridReady(page: Page): Promise<void> {
  const grid = page.locator('#flat-grid');
  await expect(grid).toBeVisible({ timeout: 30_000 });
  await expect(grid).toHaveAttribute('data-f21-init', 'true', { timeout: 30_000 });
  await expect(grid).toHaveAttribute('data-platform-admin-edit', 'true', { timeout: 5_000 });
  await page.waitForFunction(
    () =>
      typeof (window as unknown as { bootstrap?: { Modal?: unknown } }).bootstrap?.Modal !==
      'undefined',
    undefined,
    { timeout: 15_000 },
  );
}

export async function configureParkingFloors(
  page: Page,
  parkingFloors: number,
  slotsPerFloor: number,
  options?: {
    carLiftCount?: number;
    passengerLiftCount?: number;
    gateCount?: number;
  },
): Promise<void> {
  const carLiftCount = options?.carLiftCount ?? DEFAULT_PARKING_FIXTURES.carLiftCount;
  const passengerLiftCount =
    options?.passengerLiftCount ?? DEFAULT_PARKING_FIXTURES.passengerLiftCount;
  const gateCount = options?.gateCount ?? DEFAULT_PARKING_FIXTURES.gateCount;

  await waitForFlatGridReady(page);

  for (let floor = 1; floor <= parkingFloors; floor++) {
    const section = page.locator(`.flat-parking-section[data-floor-number="${floor}"]`);
    await expect(section).toBeVisible();
    const configureBtn = section.locator('.flat-parking-configure-link');
    await expect(configureBtn).toBeVisible();
    await configureBtn.scrollIntoViewIfNeeded();
    await configureBtn.click();
    const modal = page.locator('#parking-config-modal');
    await expect(modal).toHaveClass(/show/, { timeout: 15_000 });
    await expect(modal.locator('#parking-config-slots')).toBeVisible();
    await page.locator('#parking-config-slots').fill(String(slotsPerFloor));
    await page.locator('#parking-config-car-lift-count').fill(String(carLiftCount));
    await page.locator('#parking-config-passenger-lift-count').fill(String(passengerLiftCount));
    await page.locator('#parking-config-gate-count').fill(String(gateCount));

    const saveResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/parking-config') &&
        response.ok(),
      { timeout: 60_000 },
    );
    await page.locator('#parking-config-save').click();
    await saveResponse;
    await expect(modal).not.toHaveClass(/show/, { timeout: 30_000 });

    await expect(section).toHaveAttribute('data-configured', 'true', { timeout: 15_000 });
    await expect(section).toHaveClass(/flat-parking-section--split/);

    const slots = section.locator('.flat-parking-section__plan-root .parking-plan__slot');
    await expect(slots).toHaveCount(slotsPerFloor, { timeout: 30_000 });

    if (carLiftCount > 0) {
      await expect(section.locator('.parking-plan__fixture--car-lift')).toHaveCount(carLiftCount, {
        timeout: 15_000,
      });
    }
    if (passengerLiftCount > 0) {
      await expect(section.locator('.parking-plan__fixture--passenger-lift')).toHaveCount(
        passengerLiftCount,
        { timeout: 15_000 },
      );
    }
    if (gateCount > 0) {
      await expect(section.locator('.parking-plan__fixture--gate')).toHaveCount(gateCount, {
        timeout: 15_000,
      });
    }
  }
}

export async function createBuilding(page: Page, data: NewBuildingInput, projectId: string): Promise<void> {
  const form = await openNewBuildingForm(page, projectId);
  await fillNewBuildingForm(form, data);
  await submitNewBuildingForm(form, page);
  await waitForFlatGridReady(page);

  const parkingCount = expectedParkingFlatCount(data);
  const parkingFloors = data.parkingFloors ?? 0;
  if (parkingCount > 0) {
    const slotsPerFloor = defaultParkingSlotsPerFloor(data);
    const sections = page.locator('#flat-grid .flat-parking-section');
    await expect(sections).toHaveCount(parkingFloors);
    await configureParkingFloors(page, parkingFloors, slotsPerFloor, DEFAULT_PARKING_FIXTURES);
    const totalSlots = await sections.evaluateAll((els) =>
      els.reduce((sum, el) => sum + parseInt(el.getAttribute('data-slot-count') || '0', 10), 0),
    );
    expect(totalSlots).toBe(parkingCount);
  }
}

export function buildingRow(list: Locator, buildingName: string) {
  return list.locator('tbody tr').filter({ hasText: buildingName });
}

export async function firstTenantProjectId(page: Page): Promise<string> {
  const main = await openAllBuildingsList(page);
  const value = await main
    .locator('#filterProject option[value]:not([value=""])')
    .first()
    .getAttribute('value');
  if (!value) {
    throw new Error('No tenant project available. Create a project first.');
  }
  return value;
}
