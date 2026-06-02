import { test, expect } from '@playwright/test';
import { login } from '../helpers/auth';
import { createBookingForFlat, expectBookingInList } from '../helpers/bookings';
import { createClient, sampleClientData } from '../helpers/clients';
import { emitFlowCredentials } from '../helpers/flow-credentials';
import { readFlowStateFile, requireFlowState, writeFlowStateFile } from '../helpers/flow-state-file';
import {
  adminAddPartners,
  adminAssignFlats,
  adminCreateBuilding,
  adminCreateProject,
  adminCreateUsers,
  createPlatformFlowState,
  type PlatformFlowState,
} from '../helpers/platform-flow';
import { waitForMainPanel } from '../helpers/projects';

/**
 * Full Floor21 flow — admin setup then partner user journey.
 * Credentials are printed to the console / Playwright Log tab and saved to e2e/.flow-credentials.txt.
 *
 *   npm run test:ui -- tests/floor21-full-flow.spec.ts --workers=1
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
    if (!flow.clientDisplayName) {
      flow.clientDisplayName = `${flow.clientFirstName} ${flow.clientLastName}`.trim();
    }
  }
}

test.describe.serial('Floor21 — full flow (admin + partner)', () => {
  test.describe.configure({ timeout: 180_000 });

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

  test('Admin — 5. Assign flats', async ({ page }, testInfo) => {
    loadFlow();
    await adminAssignFlats(page, flow);
    expect(flow.assignToUser1.length).toBeGreaterThan(0);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-admin-complete');
  });

  test('Partner — 1. Partner login', async ({ page }, testInfo) => {
    requireFlowState(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-partner-login');
    await login(page, flow.user1.email, flow.user1.password);
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('#floor21-sidebar')).toBeVisible();
  });

  test('Partner — 2. See assigned flats on grid', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    await page.goto(`buildings/${flow.buildingId}/flats`, { waitUntil: 'commit' });
    const grid = await waitForMainPanel(page);
    await expect(grid.locator('#flat-grid')).toBeVisible();

    const bookable = grid.locator(
      '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"][data-bookable="true"]',
    );
    await expect(bookable).toHaveCount(flow.assignToUser1.length);

    const otherPartner = grid.locator(
      '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"][data-bookable="false"]',
    );
    expect(await otherPartner.count()).toBeGreaterThan(0);
  });

  test('Partner — 3. Create client', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    const client = sampleClientData(flow.stamp);
    client.firstName = flow.clientFirstName;
    client.lastName = flow.clientLastName;
    await createClient(page, client);

    await page.goto('clients', { waitUntil: 'commit' });
    const list = await waitForMainPanel(page);
    await expect(list.locator('tbody tr').filter({ hasText: flow.clientDisplayName }).first()).toBeVisible();
  });

  test('Partner — 4. Book assigned flat', async ({ page }, testInfo) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    await createBookingForFlat(page, flow.assignToUser1[0], flow.clientDisplayName);

    const bookingHeading = page.getByRole('heading', { name: /^Booking / });
    await expect(bookingHeading).toBeVisible();
    flow.bookingCode = (await bookingHeading.textContent())?.replace(/^Booking\s+/, '').trim() ?? '';
    expect(flow.bookingCode.length).toBeGreaterThan(0);
    writeFlowStateFile(flow);
    emitFlowCredentials(flow, testInfo, 'credentials-after-booking');
  });

  test('Partner — 5. Booking appears in list', async ({ page }) => {
    requireFlowState(flow);
    await login(page, flow.user1.email, flow.user1.password);
    await expectBookingInList(page, flow.clientDisplayName);
  });
});
