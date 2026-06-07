import { test, expect } from '@playwright/test';
import { login } from '../helpers/auth';
import { expectBookingInList } from '../helpers/bookings';
import { emitFlowCredentials } from '../helpers/flow-credentials';
import { readFlowStateFile, requireFlowState, writeFlowStateFile } from '../helpers/flow-state-file';
import {
  adminAddPartners,
  adminAssignFlats,
  adminConfigureColumnAUnitTypeDefaults,
  adminConfigure2BhkUnitTypeDefaults,
  adminCreateBuilding,
  adminCreateProject,
  adminCreateUsers,
  adminLinkParkingToBookedFlats,
  allPartnersCreateClientsAndBookings,
  CLIENT_BOOKING_PERCENT,
  createPlatformFlowState,
  expectPartnerBookableFlatCount,
  FLAT_ASSIGN_PERCENT,
  PARKING_LINK_SAMPLE_SIZE,
  targetClientBookingCount,
  type PlatformFlowState,
} from '../helpers/platform-flow';
import {
  DEFAULT_E2E_BHK_MIX,
  DEFAULT_E2E_BUILDING_LAYOUT,
  DEFAULT_PARKING_FIXTURES,
  expectedFlatCountForType,
  expectedParkingFlatCount,
  expectedResidentialFlatCount,
  waitForFlatGridReady,
} from '../helpers/buildings';

/**
 * Full Floor21 flow — admin setup then both partners book ≥50% of their assigned flats.
 *
 *   npm run test:ui
 */
const flow = {} as PlatformFlowState;

function initFreshFlow() {
  Object.assign(flow, createPlatformFlowState());
  flow.clientDisplayName = `${flow.clientFirstName} ${flow.clientLastName}`.trim();
  writeFlowStateFile(flow);
}

function loadFlow() {
  const saved = readFlowStateFile();
  if (saved) {
    Object.assign(flow, saved);
    flow.clients = flow.clients ?? [];
    flow.bookings = flow.bookings ?? [];
    flow.parkingLinks = flow.parkingLinks ?? [];
    flow.parkingFlatCount = flow.parkingFlatCount ?? 0;
    flow.residentialFlatCount = flow.residentialFlatCount ?? 0;
    if (!flow.clientDisplayName && flow.clients.length > 0) {
      flow.clientDisplayName = flow.clients[0].displayName;
    } else if (!flow.clientDisplayName) {
      flow.clientDisplayName = `${flow.clientFirstName} ${flow.clientLastName}`.trim();
    }
  }
}

test.describe.serial('Floor21 — full flow (admin + partner)', () => {
  test.describe.configure({ timeout: 600_000 });

  test('Admin — 1. Create project', async ({ page }, testInfo) => {
    initFreshFlow();
    await adminCreateProject(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-project');
  });

  test('Admin — 2. Create 2 users', async ({ page }, testInfo) => {
    loadFlow();
    await adminCreateUsers(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-users-created');
  });

  test('Admin — 3. Create building', async ({ page }, testInfo) => {
    loadFlow();
    await adminCreateBuilding(page, flow);
    expect(flow.building.totalFloors).toBe(DEFAULT_E2E_BUILDING_LAYOUT.totalFloors);
    expect(flow.building.parkingFloors).toBe(DEFAULT_E2E_BUILDING_LAYOUT.parkingFloors);
    expect(flow.building.flatsPerFloor).toBe(DEFAULT_E2E_BUILDING_LAYOUT.flatsPerFloor);
    expect(flow.residentialFlatCount).toBe(expectedResidentialFlatCount(flow.building));
    expect(flow.parkingFlatCount).toBe(expectedParkingFlatCount(flow.building));

    await page.goto(`buildings/${flow.buildingId}/flats`, { waitUntil: 'commit' });
    await waitForFlatGridReady(page);

    for (const bhkType of Object.keys(DEFAULT_E2E_BHK_MIX)) {
      const expectedCount = expectedFlatCountForType(flow.building, bhkType);
      await expect(
        page.locator(
          `#flat-grid [data-flat-id][data-parking="false"][data-amenity="false"][data-type="${bhkType}"]`,
        ),
      ).toHaveCount(expectedCount);
    }
    for (let floor = 1; floor <= flow.building.parkingFloors!; floor++) {
      const section = page.locator(`.flat-parking-section[data-floor-number="${floor}"]`);
      await expect(section).toHaveAttribute(
        'data-car-lift-count',
        String(DEFAULT_PARKING_FIXTURES.carLiftCount),
      );
      await expect(section).toHaveAttribute(
        'data-passenger-lift-count',
        String(DEFAULT_PARKING_FIXTURES.passengerLiftCount),
      );
      await expect(section).toHaveAttribute('data-gate-count', '0');
      await expect(section.locator('.parking-plan__fixture--car-lift')).toHaveCount(
        DEFAULT_PARKING_FIXTURES.carLiftCount,
      );
      await expect(section.locator('.parking-plan__fixture--passenger-lift')).toHaveCount(
        DEFAULT_PARKING_FIXTURES.passengerLiftCount,
      );
      await expect(section.locator('.parking-plan__fixture--gate')).toHaveCount(0);
    }

    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-building');
  });

  test('Admin — 3b. Configure 2BHK unit type defaults (save + apply)', async ({ page }, testInfo) => {
    loadFlow();
    await adminConfigure2BhkUnitTypeDefaults(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-unit-type-defaults');
  });

  test('Admin — 3c. Configure column A defaults (save + apply)', async ({ page }, testInfo) => {
    loadFlow();
    await adminConfigureColumnAUnitTypeDefaults(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-column-defaults');
  });

  test('Admin — 4. Add partners', async ({ page }, testInfo) => {
    loadFlow();
    await adminAddPartners(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-partners-added');
  });

  test('Admin — 5. Assign ~90% flats between partners', async ({ page }, testInfo) => {
    loadFlow();
    await adminAssignFlats(page, flow);

    const assigned = flow.assignToUser1.length + flow.assignToUser2.length;
    expect(assigned).toBeGreaterThanOrEqual(Math.ceil(flow.residentialFlatCount * FLAT_ASSIGN_PERCENT));
    expect(flow.assignToUser1.length).toBeGreaterThan(0);
    expect(flow.assignToUser2.length).toBeGreaterThan(0);

    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-admin-complete');
  });

  test('Partner — 1. Partner 1 sees assigned flats on grid', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    await expectPartnerBookableFlatCount(page, flow.buildingId, flow.assignToUser1.length);
  });

  test('Partner — 2. Partner 2 sees assigned flats on grid', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user2.email, flow.user2.password);
    await expectPartnerBookableFlatCount(page, flow.buildingId, flow.assignToUser2.length);
  });

  test('Partner — 3. Both partners create clients and book ≥50% of their flats', async ({ page }, testInfo) => {
    requireFlowState(flow);
    const target1 = targetClientBookingCount(flow.assignToUser1.length);
    const target2 = targetClientBookingCount(flow.assignToUser2.length);

    await allPartnersCreateClientsAndBookings(page, flow);

    const user1Bookings = flow.bookings.filter((b) => b.partnerEmail === flow.user1.email);
    const user2Bookings = flow.bookings.filter((b) => b.partnerEmail === flow.user2.email);

    expect(user1Bookings.length).toBe(target1);
    expect(user2Bookings.length).toBe(target2);
    expect(flow.clients.length).toBe(target1 + target2);

    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-bookings');
  });

  test('Admin — 6. Link parking slots to booked flats', async ({ page }, testInfo) => {
    requireFlowState(flow);
    expect(flow.bookings.length).toBeGreaterThan(0);

    await adminLinkParkingToBookedFlats(page, flow);

    expect(flow.parkingLinks.length).toBe(
      Math.min(PARKING_LINK_SAMPLE_SIZE, flow.bookings.length),
    );
    for (const link of flow.parkingLinks) {
      const section = page.locator(
        `.flat-parking-section[data-floor-number="${link.parkingFloor}"]`,
      );
      const slot = section.locator(
        `.parking-plan__slot--linked[data-slot-number="${link.slotNumber}"]`,
      );
      await expect(slot).toHaveAttribute('data-linked-flat-id', link.residentialFlatId);
      await expect(slot.locator('.parking-plan__slot-flat')).toContainText(link.residentialFlatNumber);
    }

    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-parking-links');
  });

  test('Partner — 4. Own bookings appear in list (partner 1)', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    for (const booking of flow.bookings.filter((b) => b.partnerEmail === flow.user1.email)) {
      await expectBookingInList(page, booking.clientDisplayName);
    }
  });

  test('Partner — 5. Own bookings appear in list (partner 2)', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user2.email, flow.user2.password);
    for (const booking of flow.bookings.filter((b) => b.partnerEmail === flow.user2.email)) {
      await expectBookingInList(page, booking.clientDisplayName);
    }
  });
});
