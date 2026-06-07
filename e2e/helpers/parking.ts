import { expect, type Page } from '@playwright/test';
import { defaultParkingSlotsPerFloor, openFlatDetailsForFlat, type NewBuildingInput } from './buildings';
import { waitForMainPanel } from './projects';

export type ParkingSlotLocation = {
  floor: number;
  slotNumber: number;
};

/** How many booked flats to link in the full-flow parking test (random sample). */
export const PARKING_LINK_SAMPLE_SIZE = 3;

/** Fisher–Yates shuffle, then take the first `count` items. */
export function pickRandomSample<T>(items: T[], count: number): T[] {
  if (items.length <= count) return [...items];
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, count);
}

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

  const detailsModal = page.locator('#flat-details-modal');
  await expect(detailsModal).toBeVisible({ timeout: 30_000 });
  await detailsModal.locator('#panel-parking-slot-link-btn').click();

  const modal = page.locator('#parking-link-modal');
  await expect(modal).toBeVisible();
  const select = page.locator('#parking-link-flat');
  await expect(select).not.toBeDisabled({ timeout: 30_000 });
  await select.selectOption(residentialFlatId);
  await page.locator('#parking-link-save').click();
  await expect(modal).not.toBeVisible({ timeout: 30_000 });

  const linkedSlot = section.locator(
    `.parking-plan__slot--linked[data-slot-number="${slotNumber}"]`,
  );
  await expect(linkedSlot).toBeVisible({ timeout: 30_000 });
  await expect(linkedSlot).toHaveAttribute('data-linked-flat-id', residentialFlatId);

  if (await detailsModal.isVisible()) {
    await detailsModal.locator('.btn-close').click();
    await expect(detailsModal).toBeHidden();
  }

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
  const modal = await openFlatDetailsForFlat(page, residentialFlatId);
  const parkingLinks = modal.locator('#panel-parking-links');
  await expect(parkingLinks).toBeVisible();
  const list = parkingLinks.locator('#panel-parking-links-list');
  await expect(list.locator('li')).toHaveCount(1, { timeout: 30_000 });
  await expect(list).toContainText(`Floor ${parkingFloor}`);
  await expect(list).toContainText(`Slot ${slotNumber}`);
  await modal.locator('.btn-close').click();
  await expect(modal).toBeHidden();
}
