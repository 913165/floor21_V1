import { expect, type Locator, type Page } from '@playwright/test';
import {
  openAllBuildingsList as openAllBuildingsListViaNav,
  openNewBuildingForm as openNewBuildingFormViaNav,
} from './nav';
import { waitForMainPanel } from './projects';

/** Residential unit types shown on the admin building layout form. */
export const LAYOUT_BHK_TYPES = [
  'STUDIO',
  '1BHK',
  '1.5BHK',
  '2BHK',
  '2.5BHK',
  '3BHK',
  '3.5BHK',
  '4BHK',
  '4.5BHK',
  '5BHK',
  '5.5BHK',
  '6BHK',
  '6.5BHK',
  '7BHK',
] as const;

/** Default E2E mix per residential floor: 1×1BHK, 3×2BHK, 1×3BHK (must sum to flatsPerFloor). */
export const DEFAULT_E2E_BHK_MIX: Record<string, number> = {
  '1BHK': 1,
  '2BHK': 3,
  '3BHK': 1,
};

/** 9 total floors = 3 parking + 6 residential; 5 units per residential floor. */
export const DEFAULT_E2E_BUILDING_LAYOUT = {
  totalFloors: 9,
  parkingFloors: 3,
  flatsPerFloor: 5,
  bhkPerFloor: DEFAULT_E2E_BHK_MIX,
} as const;

export type NewBuildingInput = {
  name: string;
  totalFloors?: number;
  parkingFloors?: number;
  flatsPerFloor?: number;
  /** Per-floor unit mix; all layout types are zeroed then these counts are applied. */
  bhkPerFloor?: Record<string, number>;
  /** @deprecated Use bhkPerFloor instead. */
  twoBhkPerFloor?: number;
  city?: string;
  address?: string;
};

export function resolveBhkMix(data: NewBuildingInput): Record<string, number> {
  if (data.bhkPerFloor && Object.keys(data.bhkPerFloor).length > 0) {
    return { ...data.bhkPerFloor };
  }
  if (data.twoBhkPerFloor != null) {
    return { '2BHK': data.twoBhkPerFloor };
  }
  return { ...DEFAULT_E2E_BHK_MIX };
}

export function expectedFlatCountForType(data: NewBuildingInput, bhkType: string): number {
  const totalFloors = data.totalFloors ?? DEFAULT_E2E_BUILDING_LAYOUT.totalFloors;
  const parkingFloors = data.parkingFloors ?? 0;
  const residentialFloors = Math.max(0, totalFloors - parkingFloors);
  const mix = resolveBhkMix(data);
  return (mix[bhkType] ?? 0) * residentialFloors;
}

function bhkPerFloorInput(main: Locator, bhkType: string): Locator {
  return main.locator(`input[name="bhkPerFloor['${bhkType}']"]`);
}

export function expectedResidentialFlatCount(data: NewBuildingInput): number {
  const totalFloors = data.totalFloors ?? DEFAULT_E2E_BUILDING_LAYOUT.totalFloors;
  const parkingFloors = data.parkingFloors ?? DEFAULT_E2E_BUILDING_LAYOUT.parkingFloors;
  const flatsPerFloor = data.flatsPerFloor ?? DEFAULT_E2E_BUILDING_LAYOUT.flatsPerFloor;
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
    ...DEFAULT_E2E_BUILDING_LAYOUT,
    city: 'Mumbai',
    address: `E2E Building Address ${index}, Andheri`,
  };
}

export async function openAllBuildingsList(
  page: Page,
  options?: { projectId?: string },
): Promise<Locator> {
  return openAllBuildingsListViaNav(page, options);
}

export async function openNewBuildingForm(page: Page, projectId: string): Promise<Locator> {
  return openNewBuildingFormViaNav(page, projectId);
}

export { openAdminBuildingFlatGrid, openBuildingFlatGrid } from './nav';

export async function fillNewBuildingForm(main: Locator, data: NewBuildingInput) {
  const buildingName = main.locator('input#buildingName');
  await expect(buildingName).toBeVisible({ timeout: 15_000 });
  await buildingName.fill(data.name);
  const totalFloors = data.totalFloors ?? DEFAULT_E2E_BUILDING_LAYOUT.totalFloors;
  const parkingFloors = data.parkingFloors ?? DEFAULT_E2E_BUILDING_LAYOUT.parkingFloors;
  const flatsPerFloor = data.flatsPerFloor ?? DEFAULT_E2E_BUILDING_LAYOUT.flatsPerFloor;
  const mix = resolveBhkMix({ ...data, totalFloors, parkingFloors, flatsPerFloor });

  await main.locator('#totalFloors').fill(String(totalFloors));
  await main.locator('#parkingFloors').fill(String(parkingFloors));
  await main.locator('#flatsPerFloor').fill(String(flatsPerFloor));

  for (const bhkType of LAYOUT_BHK_TYPES) {
    await bhkPerFloorInput(main, bhkType).fill(String(mix[bhkType] ?? 0));
  }

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

/** Opens flat details modal for a flat card (uses floor21SelectFlat so selectedFlatId stays in sync). */
export async function openFlatDetailsForFlat(page: Page, flatId: string): Promise<Locator> {
  const card = page.locator(`#flat-${flatId}.flat-card`);
  await expect(card).toBeVisible();
  await card.scrollIntoViewIfNeeded();

  const modal = page.locator('#flat-details-modal');
  await page.evaluate((id) => {
    const cardEl = document.getElementById(`flat-${id}`);
    if (cardEl && typeof (window as Window & { floor21SelectFlat?: (el: Element, show?: boolean) => void }).floor21SelectFlat === 'function') {
      (window as Window & { floor21SelectFlat: (el: Element, show?: boolean) => void }).floor21SelectFlat(cardEl, true);
    }
  }, flatId);

  await expect(modal).toBeVisible();
  return modal;
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
  return list.locator('tbody tr').filter({
    has: list.page().getByRole('cell', { name: buildingName }),
  });
}

export function buildingRowById(list: Locator, buildingId: string) {
  const id = buildingId.toLowerCase();
  return list.locator(`tbody tr:has(a[href*="${id}"])`);
}

const LIST_LOOKUP_ATTEMPTS = 12;
const LIST_LOOKUP_INTERVAL_MS = 1_500;

export type BuildingListMatch = {
  row: Locator;
  flatsLink: Locator;
};

/**
 * Scan buildings table rows (like manual lookup): reload the filtered list and loop
 * each row until we find the building (by data-building-id, then Flats href fallback).
 */
export async function waitForBuildingInList(
  page: Page,
  options: {
    projectId: string;
    buildingId: string;
    projectName: string;
    buildingName: string;
    attempts?: number;
    intervalMs?: number;
  },
): Promise<BuildingListMatch> {
  const attempts = options.attempts ?? LIST_LOOKUP_ATTEMPTS;
  const intervalMs = options.intervalMs ?? LIST_LOOKUP_INTERVAL_MS;
  const id = options.buildingId.toLowerCase();

  for (let attempt = 1; attempt <= attempts; attempt++) {
    const list = await openAllBuildingsList(page, { projectId: options.projectId });
    await expect(list.locator('#filterProject')).toHaveValue(options.projectId);

    const match = await findBuildingRowInList(list, id, options.projectName, options.buildingName);
    if (match) {
      await expect(match.flatsLink).toBeVisible();
      return match;
    }

    if (attempt < attempts) {
      await page.waitForTimeout(intervalMs);
    }
  }

  throw new Error(
    `Building ${options.buildingId} (${options.buildingName}) not found in All buildings ` +
      `for project ${options.projectName} after ${attempts} lookup attempts.`,
  );
}

function flatsLinkInRow(row: Locator, buildingId: string): Locator {
  const id = buildingId.toLowerCase();
  return row.locator(`a[data-building-id="${id}"], a[href*="/buildings/${id}/flats"]`).first();
}

async function findBuildingRowInList(
  list: Locator,
  buildingId: string,
  projectName: string,
  buildingName: string,
): Promise<BuildingListMatch | null> {
  const id = buildingId.toLowerCase();
  const byId = list.locator(`tbody tr[data-building-id="${id}"]`);
  if ((await byId.count()) > 0) {
    const row = byId.first();
    if (await rowMatchesBuilding(row, buildingId, projectName, buildingName)) {
      return { row, flatsLink: flatsLinkInRow(row, buildingId) };
    }
  }

  const rows = list.locator('tbody tr');
  const rowCount = await rows.count();
  for (let i = 0; i < rowCount; i++) {
    const row = rows.nth(i);
    if (await rowMatchesBuilding(row, buildingId, projectName, buildingName)) {
      return { row, flatsLink: flatsLinkInRow(row, buildingId) };
    }
  }

  return null;
}

async function rowMatchesBuilding(
  row: Locator,
  buildingId: string,
  projectName: string,
  buildingName: string,
): Promise<boolean> {
  const id = buildingId.toLowerCase();
  const dataId = ((await row.getAttribute('data-building-id')) ?? '').toLowerCase();
  const dataName = ((await row.getAttribute('data-building-name')) ?? '').trim();
  const flatsLink = flatsLinkInRow(row, buildingId);
  if ((await flatsLink.count()) === 0) {
    return false;
  }

  if (dataId && dataId !== id) {
    return false;
  }

  const href = ((await flatsLink.getAttribute('href')) ?? '').toLowerCase();
  if (!href.includes(id) || !href.includes('/flats')) {
    return false;
  }

  const rowText = ((await row.textContent()) ?? '').replace(/\s+/g, ' ').trim();
  if (!rowText.includes(projectName)) {
    return false;
  }

  if (dataName) {
    return dataName === buildingName || rowText.includes(buildingName);
  }
  return rowText.includes(buildingName);
}

/** Scan an already-open list panel (no reload/retry). */
export async function expectBuildingListed(
  list: Locator,
  projectId: string,
  buildingId: string,
  projectName: string,
  buildingName: string,
): Promise<void> {
  await expect(list.locator('#filterProject')).toHaveValue(projectId);
  const match = await findBuildingRowInList(list, buildingId.toLowerCase(), projectName, buildingName);
  if (!match) {
    throw new Error(
      `Building ${buildingId} (${buildingName}) not found when scanning the open list.`,
    );
  }
  await expect(match.flatsLink).toBeVisible();
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
