import { expect, type Page } from '@playwright/test';
import { defaultParkingSlotsPerFloor, type NewBuildingInput } from './buildings';
import { waitForMainPanel } from './projects';

export type ParkingSlotLocation = {
  floor: number;
  slotNumber: number;
};

/** Round-robin across parking floors, then next slot number on each floor. */
export function parkingSlotLocationForIndex(
  index: number,
  building: NewBuildingInput,
): ParkingSlotLocation {
  const parkingFloors = building.parkingFloors ?? 1;
  const slotsPerFloor = defaultParkingSlotsPerFloor(building);
  const slotNumber = Math.floor(index / parkingFloors) + 1;
  const floor = (index % parkingFloors) + 1;
  if (slotNumber > slotsPerFloor) {
    throw new Error(
      `Not enough parking slots for link index ${index} (${slotsPerFloor} slots/floor × ${parkingFloors} floors).`,
    );
  }
  return { floor, slotNumber };
}

export async function openBuildingFlatGrid(page: Page, buildingId: string) {
  await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
  const main = await waitForMainPanel(page);
  await expect(main.locator('#flat-grid')).toBeVisible({ timeout: 30_000 });
  await expect(main.locator('#flat-grid')).toHaveAttribute('data-platform-admin-edit', 'true');
  return main;
}

export async function linkParkingSlotToResidentialFlat(
  page: Page,
  parkingFloor: number,
  slotNumber: number,
  residentialFlatId: string,
): Promise<string> {
  const section = page.locator(`.flat-parking-section[data-floor-number="${parkingFloor}"]`);
  await expect(section).toBeVisible();
  await expect(section).toHaveAttribute('data-configured', 'true');

  const slot = section.locator(
    `.parking-plan__slot--clickable[data-slot-number="${slotNumber}"]`,
  );
  await expect(slot).toBeVisible();
  const parkingFlatId = (await slot.getAttribute('data-parking-flat-id')) ?? '';
  expect(parkingFlatId.length).toBeGreaterThan(0);

  await slot.scrollIntoViewIfNeeded();
  await slot.click();

  const modal = page.locator('#parking-link-modal');
  await expect(modal).toBeVisible();
  const select = page.locator('#parking-link-flat');
  await expect(select).not.toBeDisabled({ timeout: 30_000 });
  await select.selectOption(residentialFlatId);
  await page.locator('#parking-link-save').click();
  await expect(modal).not.toBeVisible({ timeout: 30_000 });

  await expect(slot).toHaveClass(/parking-plan__slot--linked/);
  await expect(slot).toHaveAttribute('data-linked-flat-id', residentialFlatId);

  return parkingFlatId;
}

export async function expectResidentialFlatShowsParkingLink(
  page: Page,
  residentialFlatId: string,
  parkingFloor: number,
  slotNumber: number,
) {
  const card = page.locator(`#flat-${residentialFlatId}`);
  await card.scrollIntoViewIfNeeded();
  await card.locator('.flat-quick-link').click();

  const modal = page.locator('#flat-details-modal');
  await expect(modal).toBeVisible();
  const parkingLinks = modal.locator('#panel-parking-links');
  await expect(parkingLinks).toBeVisible();
  const list = parkingLinks.locator('#panel-parking-links-list');
  await expect(list.locator('li')).toHaveCount(1, { timeout: 30_000 });
  await expect(list).toContainText(`Floor ${parkingFloor}`);
  await expect(list).toContainText(`Slot ${slotNumber}`);
  await modal.locator('.btn-close').click();
  await expect(modal).toBeHidden();
}
