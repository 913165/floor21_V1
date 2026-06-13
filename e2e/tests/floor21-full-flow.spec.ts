import { test, expect } from '@playwright/test';
import { login } from '../helpers/auth';
import { openAdminBuildingFlatGrid } from '../helpers/nav';
import { expectBookingInList } from '../helpers/bookings';
import { E2E_PRIMARY_BUYER } from '../helpers/clients';
import { emitFlowCredentials } from '../helpers/flow-credentials';
import {
  readFlowStateFile,
  requireBuildingFlow,
  requireFlowState,
  writeFlowStateFile,
} from '../helpers/flow-state-file';
import { adminImportMilestoneTemplates, ensureClientMilestoneSchedule } from '../helpers/milestones';
import {
  adminAddPartners,
  adminAssignFlats,
  adminConfigureColumnAUnitTypeDefaults,
  adminConfigure2BhkUnitTypeDefaults,
  adminSaveFlatDetailsAreas,
  adminConfigureFloorSizes,
  adminCreateBuilding,
  adminCreateProject,
  adminCreateUsers,
  adminLinkParkingToBookedFlats,
  allPartnersCreateClientsAndBookings,
  CLIENT_BOOKING_PERCENT,
  createPlatformFlowState,
  expectOwnerBookableFlatCount,
  expectPartnerBookableFlatCount,
  FLAT_ASSIGN_PERCENT,
  PARKING_LINK_SAMPLE_SIZE,
  targetClientBookingCount,
  type FlowBookingRecord,
  type PlatformFlowState,
} from '../helpers/platform-flow';
import {
  createPaymentReceipt,
  E2E_MIN_RECEIPTS_FOR_SLAB_TEST,
  e2eReceiptAmountsForSlabWaterfall,
  e2eReceiptDate,
  screenshotPaymentSchedule,
} from '../helpers/receipts';
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
    flow.receipts = flow.receipts ?? [];
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

    await openAdminBuildingFlatGrid(page, {
      buildingId: flow.buildingId,
      projectId: flow.projectId,
    });
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
    requireBuildingFlow(flow);
    await adminConfigure2BhkUnitTypeDefaults(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-unit-type-defaults');
  });

  test('Admin — 3c. Configure column A defaults (save + apply)', async ({ page }, testInfo) => {
    loadFlow();
    requireBuildingFlow(flow);
    await adminConfigureColumnAUnitTypeDefaults(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-column-defaults');
  });

  test('Admin — 3d. Per-flat area save (sq m toggle, Saved values, reopen)', async ({
    page,
  }, testInfo) => {
    loadFlow();
    requireBuildingFlow(flow);
    await adminSaveFlatDetailsAreas(page, flow);
    expect(flow.flatDetailsAreasTest?.flatId).toBeTruthy();
    expect(flow.flatDetailsAreasTest?.balconySqm).toBe(10);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-flat-details-areas');
  });

  test('Admin — 3e. Ground floor shops + parking configure (no size sliders)', async ({
    page,
  }, testInfo) => {
    loadFlow();
    requireBuildingFlow(flow);
    await adminConfigureFloorSizes(page, flow);
    expect(flow.floorSizeConfigTest?.parkingCarSizePercent).toBe(180);
    expect(flow.floorSizeConfigTest?.shopSizePercent).toBe(140);
    expect(flow.floorSizeConfigTest?.groundShopCount).toBe(4);
    expect(flow.floorSizeConfigTest?.parkingFloor).toBe(1);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-floor-size-config');
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

  test('Admin — 5b. Import milestone templates for E2E building', async ({ page }, testInfo) => {
    loadFlow();
    requireBuildingFlow(flow);
    await adminImportMilestoneTemplates(page, flow);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-milestones-imported');
  });

  test('Partner — 1. Partner 1 sees assigned flats on grid', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    await expectPartnerBookableFlatCount(page, flow.buildingId, flow.assignToUser1.length);
  });

  test('Partner — 2. Owner sees all residential flats bookable on grid', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user2.email, flow.user2.password);
    await expectOwnerBookableFlatCount(page, flow.buildingId, flow.assignToUser2);
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

  test('Partner — 6. Payment receipts (5+ slabs) + schedule screenshots', async ({ page }, testInfo) => {
    requireFlowState(flow);
    const primaryBuyerBooking = flow.bookings.find(
      (b) => b.clientEmail === E2E_PRIMARY_BUYER.email,
    );
    const user2Booking = flow.bookings.find(
      (b) => b.partnerEmail === flow.user2.email && b.clientEmail !== E2E_PRIMARY_BUYER.email,
    );
    expect(primaryBuyerBooking).toBeTruthy();
    expect(user2Booking).toBeTruthy();

    const primaryAmounts = e2eReceiptAmountsForSlabWaterfall();
    expect(primaryAmounts.length).toBeGreaterThanOrEqual(E2E_MIN_RECEIPTS_FOR_SLAB_TEST);

    flow.receipts = [];

    await login(page, flow.user1.email, flow.user1.password);
    await ensureClientMilestoneSchedule(page, flow, primaryBuyerBooking!.clientDisplayName);

    for (let i = 0; i < primaryAmounts.length; i++) {
      const amount = primaryAmounts[i];
      const chequeNo = await createPaymentReceipt(
        page,
        flow,
        primaryBuyerBooking!.clientDisplayName,
        amount,
        { receiptIndex: i, receiptDate: e2eReceiptDate(i) },
      );
      flow.receipts.push({
        partnerEmail: primaryBuyerBooking!.partnerEmail,
        clientDisplayName: primaryBuyerBooking!.clientDisplayName,
        amount,
        chequeNo,
      });
    }

    const primaryShot = testInfo.outputPath('payment-schedule-primary-5-receipts.png');
    await screenshotPaymentSchedule(page, flow, primaryBuyerBooking!.clientDisplayName, primaryShot);
    await testInfo.attach(`payment-schedule-${primaryBuyerBooking!.clientDisplayName}`, {
      path: primaryShot,
      contentType: 'image/png',
    });

    await login(page, flow.user2.email, flow.user2.password);
    await ensureClientMilestoneSchedule(page, flow, user2Booking!.clientDisplayName);
    const partner2Amount = primaryAmounts[0];
    const partner2Cheque = await createPaymentReceipt(
      page,
      flow,
      user2Booking!.clientDisplayName,
      partner2Amount,
      { receiptIndex: primaryAmounts.length, receiptDate: e2eReceiptDate(primaryAmounts.length) },
    );
    flow.receipts.push({
      partnerEmail: user2Booking!.partnerEmail,
      clientDisplayName: user2Booking!.clientDisplayName,
      amount: partner2Amount,
      chequeNo: partner2Cheque,
    });

    const partner2Shot = testInfo.outputPath('payment-schedule-partner2.png');
    await screenshotPaymentSchedule(page, flow, user2Booking!.clientDisplayName, partner2Shot);
    await testInfo.attach(`payment-schedule-${user2Booking!.clientDisplayName}`, {
      path: partner2Shot,
      contentType: 'image/png',
    });

    expect(flow.receipts.length).toBeGreaterThanOrEqual(E2E_MIN_RECEIPTS_FOR_SLAB_TEST);

    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-receipts');
  });
});
