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

export function expectedParkingFlatCount(data: NewBuildingInput): number {
  const parkingFloors = data.parkingFloors ?? 0;
  const flatsPerFloor = data.flatsPerFloor ?? 4;
  return parkingFloors * flatsPerFloor;
}

export function expectedResidentialFlatCount(data: NewBuildingInput): number {
  const totalFloors = data.totalFloors ?? 5;
  const parkingFloors = data.parkingFloors ?? 0;
  const flatsPerFloor = data.flatsPerFloor ?? 4;
  return Math.max(0, totalFloors - parkingFloors) * flatsPerFloor;
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

export async function createBuilding(page: Page, data: NewBuildingInput, projectId: string): Promise<void> {
  const form = await openNewBuildingForm(page, projectId);
  await fillNewBuildingForm(form, data);
  await submitNewBuildingForm(form, page);
  await expect(page.locator('#flat-grid')).toBeVisible({ timeout: 30_000 });

  const parkingCount = expectedParkingFlatCount(data);
  if (parkingCount > 0) {
    const parking = page.locator('#flat-grid [data-flat-id][data-parking="true"]');
    await expect(parking).toHaveCount(parkingCount);
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
