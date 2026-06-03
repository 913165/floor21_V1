import { test, expect } from '@playwright/test';
import { login } from '../helpers/auth';
import { expectBookingInList } from '../helpers/bookings';
import { emitFlowCredentials } from '../helpers/flow-credentials';
import { readFlowStateFile, requireFlowState, writeFlowStateFile } from '../helpers/flow-state-file';
import {
  adminAddPartners,
  adminAssignFlats,
  adminCreateBuilding,
  adminCreateProject,
  adminCreateUsers,
  allPartnersCreateClientsAndBookings,
  CLIENT_BOOKING_PERCENT,
  createPlatformFlowState,
  expectPartnerBookableFlatCount,
  FLAT_ASSIGN_PERCENT,
  targetClientBookingCount,
  type PlatformFlowState,
} from '../helpers/platform-flow';

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
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-building');
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

  test('Partner — 4. All bookings appear in list (partner 1)', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    for (const booking of flow.bookings) {
      await expectBookingInList(page, booking.clientDisplayName);
    }
  });

  test('Partner — 5. All bookings appear in list (partner 2)', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user2.email, flow.user2.password);
    for (const booking of flow.bookings) {
      await expectBookingInList(page, booking.clientDisplayName);
    }
  });
});
