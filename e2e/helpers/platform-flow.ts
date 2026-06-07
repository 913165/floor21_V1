import { expect, type Page } from '@playwright/test';
import {
  createBuilding,
  expectedFlatCountForType,
  expectedParkingFlatCount,
  expectedResidentialFlatCount,
  openFlatDetailsForFlat,
  sampleBuildingData,
  waitForBuildingInList,
  waitForFlatGridReady,
  type NewBuildingInput,
} from './buildings';
import { createBookingForFlat } from './bookings';
import { createClient, sampleClientData, type NewClientInput } from './clients';
import {
  expectResidentialFlatShowsParkingLink,
  linkParkingSlotToResidentialFlat,
  openBuildingFlatGrid,
  PARKING_LINK_SAMPLE_SIZE,
  parkingSlotLocationForIndex,
  pickRandomSample,
} from './parking';
import { createProject, uniqueProjectName, waitForMainPanel } from './projects';
import { createUser, sampleUserData, type NewUserInput } from './users';
import { login, loginAsSuperAdmin } from './auth';
import {
  configureAndApplyColumnTypeDefaults,
  E2E_COLUMN_1_DEFAULTS,
  type ColumnTypeDefaultsInput,
} from './column-type-defaults';
import {
  configureAndApplyUnitTypeDefaults,
  E2E_2BHK_UNIT_DEFAULTS,
  type UnitTypeDefaultsInput,
} from './unit-type-defaults';

export { PARKING_LINK_SAMPLE_SIZE } from './parking';
/** Share of residential flats assigned to partners (remainder stays unassigned). */
export const FLAT_ASSIGN_PERCENT = 0.9;
/** Minimum share of partner-assigned flats that get a client + booking in E2E. */
export const CLIENT_BOOKING_PERCENT = 0.5;

export type FlowClientRecord = {
  partnerEmail: string;
  firstName: string;
  lastName: string;
  displayName: string;
};

export type FlowBookingRecord = {
  partnerEmail: string;
  flatId: string;
  clientDisplayName: string;
  bookingCode: string;
};

export type FlowParkingLinkRecord = {
  parkingFloor: number;
  slotNumber: number;
  parkingFlatId: string;
  residentialFlatId: string;
  residentialFlatNumber: string;
};

export type PlatformFlowState = {
  stamp: number;
  projectName: string;
  projectId: string;
  user1: NewUserInput;
  user2: NewUserInput;
  building: NewBuildingInput;
  buildingId: string;
  parkingFlatCount: number;
  residentialFlatCount: number;
  assignToUser1: string[];
  assignToUser2: string[];
  clients: FlowClientRecord[];
  bookings: FlowBookingRecord[];
  parkingLinks: FlowParkingLinkRecord[];
  clientFirstName: string;
  clientLastName: string;
  clientDisplayName: string;
  bookingCode: string;
  unitTypeDefaults2Bhk?: UnitTypeDefaultsInput;
  columnTypeDefaults1?: ColumnTypeDefaultsInput;
};

export function createPlatformFlowState(): PlatformFlowState {
  const stamp = Date.now();
  return {
    stamp,
    projectName: uniqueProjectName('E2E Flow'),
    projectId: '',
    user1: {
      ...sampleUserData(1),
      fullName: 'Aarav Sharma',
      email: `aarav.sharma.${stamp}@example.test`,
    },
    user2: {
      ...sampleUserData(2),
      fullName: 'Priya Nair',
      email: `priya.nair.${stamp}@example.test`,
    },
    building: {
      ...sampleBuildingData(1),
      name: `E2E Flow Tower ${stamp}`,
      city: 'Mumbai',
      address: `E2E Flow Address, Andheri ${stamp}`,
    },
    buildingId: '',
    parkingFlatCount: 0,
    residentialFlatCount: 0,
    assignToUser1: [],
    assignToUser2: [],
    clients: [],
    bookings: [],
    parkingLinks: [],
    clientFirstName: `E2E Client ${stamp}`,
    clientLastName: 'Buyer',
    clientDisplayName: '',
    bookingCode: '',
  };
}

export async function adminCreateProject(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  const { projectId } = await createProject(page, {
    name: flow.projectName,
    city: 'Mumbai',
    address: `E2E Flow Project Address ${flow.stamp}`,
  });
  flow.projectId = projectId;
}

export async function adminCreateUsers(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await createUser(page, flow.user1);
  await createUser(page, flow.user2);
}

export async function adminCreateBuilding(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await createBuilding(page, flow.building, flow.projectId);
  const buildingId = page.url().match(/\/buildings\/([0-9a-f-]+)\/flats/i)?.[1];
  if (!buildingId) {
    throw new Error(`Could not parse building id from ${page.url()}`);
  }
  flow.buildingId = buildingId;
  flow.parkingFlatCount = expectedParkingFlatCount(flow.building);
  flow.residentialFlatCount = expectedResidentialFlatCount(flow.building);

  const residential = page.locator('#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"]');
  await expect(residential).toHaveCount(flow.residentialFlatCount);

  const savedName = await page.locator('#floor21-main h1 ~ div.text-muted.small').textContent();
  if (savedName?.trim()) {
    flow.building.name = savedName.trim();
  }

  await waitForBuildingInList(page, {
    projectId: flow.projectId,
    buildingId: flow.buildingId,
    projectName: flow.projectName,
    buildingName: flow.building.name,
  });
}

export async function adminConfigureColumnAUnitTypeDefaults(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  const defaults = flow.columnTypeDefaults1 ?? E2E_COLUMN_1_DEFAULTS;
  await configureAndApplyColumnTypeDefaults(page, flow.buildingId, flow.building, defaults);
  flow.columnTypeDefaults1 = defaults;
}

export async function adminConfigure2BhkUnitTypeDefaults(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  const defaults = flow.unitTypeDefaults2Bhk ?? E2E_2BHK_UNIT_DEFAULTS;
  const expectedCount = expectedFlatCountForType(flow.building, defaults.bhkType);
  await configureAndApplyUnitTypeDefaults(page, flow.buildingId, defaults, expectedCount);
  flow.unitTypeDefaults2Bhk = defaults;
}

export async function adminAddPartners(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await addPartnerToProject(page, flow.projectId, flow.user1, flow.buildingId);
  await addPartnerToProject(page, flow.projectId, flow.user2, flow.buildingId);
}

export async function adminAssignFlats(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await page.goto(`buildings/${flow.buildingId}/flats`, { waitUntil: 'commit' });
  await waitForFlatGridReady(page);
  const adminGrid = await waitForMainPanel(page);
  await expect(adminGrid.locator('#flat-grid')).toBeVisible();

  const candidateFlats = adminGrid.locator(
    '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"]',
  );
  const candidateCount = await candidateFlats.count();
  expect(candidateCount).toBeGreaterThanOrEqual(4);
  flow.residentialFlatCount = candidateCount;

  const flatIds: string[] = [];
  for (let i = 0; i < candidateCount; i++) {
    const id = await candidateFlats.nth(i).getAttribute('data-flat-id');
    if (id) flatIds.push(id);
  }

  const assignCount = Math.max(2, Math.ceil(flatIds.length * FLAT_ASSIGN_PERCENT));
  const shuffled = shuffle(flatIds);
  const toAssign = shuffled.slice(0, assignCount);
  const splitAt = Math.ceil(toAssign.length / 2);
  flow.assignToUser1 = toAssign.slice(0, splitAt);
  flow.assignToUser2 = toAssign.slice(splitAt);

  for (const flatId of flow.assignToUser1) {
    await assignFlatToPartner(page, flatId, flow.user1.fullName);
  }
  for (const flatId of flow.assignToUser2) {
    await assignFlatToPartner(page, flatId, flow.user2.fullName);
  }

  const assignedTotal = flow.assignToUser1.length + flow.assignToUser2.length;
  expect(assignedTotal).toBeGreaterThanOrEqual(Math.ceil(flatIds.length * FLAT_ASSIGN_PERCENT));
}

export function targetClientBookingCount(assignedFlatCount: number): number {
  return Math.max(1, Math.ceil(assignedFlatCount * CLIENT_BOOKING_PERCENT));
}

/** Create clients + bookings for ≥50% of flats assigned to one partner. Caller must be logged in as that partner. */
export async function partnerCreateClientsAndBookings(
  page: Page,
  flow: PlatformFlowState,
  partner: NewUserInput,
  assignedFlatIds: string[],
  clientIdPrefix: string,
): Promise<{ clients: FlowClientRecord[]; bookings: FlowBookingRecord[] }> {
  const target = targetClientBookingCount(assignedFlatIds.length);
  const clients: FlowClientRecord[] = [];
  const bookings: FlowBookingRecord[] = [];
  const clientIndexOffset = partner.email === flow.user2.email ? 100 : 0;

  for (let i = 0; i < target; i++) {
    const client: NewClientInput = {
      ...sampleClientData(flow.stamp, clientIndexOffset + i + 1),
      firstName: `${clientIdPrefix} C${i + 1}`,
      lastName: 'Buyer',
    };
    await createClient(page, client);
    const displayName = `${client.firstName} ${client.lastName}`.trim();
    clients.push({
      partnerEmail: partner.email,
      firstName: client.firstName,
      lastName: client.lastName,
      displayName,
    });

    const flatId = assignedFlatIds[i];
    await createBookingForFlat(page, flatId, displayName);

    const bookingHeading = page.getByRole('heading', { name: /^Booking / });
    await expect(bookingHeading).toBeVisible();
    const bookingCode =
      (await bookingHeading.textContent())?.replace(/^Booking\s+/, '').trim() ?? '';
    expect(bookingCode.length).toBeGreaterThan(0);
    bookings.push({
      partnerEmail: partner.email,
      flatId,
      clientDisplayName: displayName,
      bookingCode,
    });
  }

  return { clients, bookings };
}

/** Both partners each book ≥50% of their own assigned flats. */
export async function allPartnersCreateClientsAndBookings(page: Page, flow: PlatformFlowState) {
  flow.clients = [];
  flow.bookings = [];

  await login(page, flow.user1.email, flow.user1.password);
  await expect(page).toHaveURL(/\/dashboard/);
  const p1 = await partnerCreateClientsAndBookings(
    page,
    flow,
    flow.user1,
    flow.assignToUser1,
    `E2E ${flow.stamp} P1`,
  );
  flow.clients.push(...p1.clients);
  flow.bookings.push(...p1.bookings);

  await login(page, flow.user2.email, flow.user2.password);
  await expect(page).toHaveURL(/\/dashboard/);
  const p2 = await partnerCreateClientsAndBookings(
    page,
    flow,
    flow.user2,
    flow.assignToUser2,
    `E2E ${flow.stamp} P2`,
  );
  flow.clients.push(...p2.clients);
  flow.bookings.push(...p2.bookings);

  if (flow.clients.length > 0) {
    flow.clientFirstName = flow.clients[0].firstName;
    flow.clientLastName = flow.clients[0].lastName;
    flow.clientDisplayName = flow.clients[0].displayName;
    flow.bookingCode = flow.bookings[flow.bookings.length - 1]?.bookingCode ?? '';
  }
}

/** Super admin links parking slots to a random sample of booked residential flats. */
export async function adminLinkParkingToBookedFlats(page: Page, flow: PlatformFlowState) {
  if (!flow.bookings.length) {
    throw new Error('No bookings to link parking for.');
  }

  await loginAsSuperAdmin(page);
  await openBuildingFlatGrid(page, flow.buildingId);

  flow.parkingLinks = [];
  const bookingsToLink = pickRandomSample(flow.bookings, PARKING_LINK_SAMPLE_SIZE);

  for (let i = 0; i < bookingsToLink.length; i++) {
    const booking = bookingsToLink[i];
    const { floor, slotNumber } = parkingSlotLocationForIndex(i, flow.building);
    const residentialFlatNumber =
      (await page.locator(`#flat-${booking.flatId} .flat-number`).textContent())?.trim() ?? '';
    expect(residentialFlatNumber.length).toBeGreaterThan(0);

    const parkingFlatId = await linkParkingSlotToResidentialFlat(
      page,
      floor,
      slotNumber,
      booking.flatId,
    );

    flow.parkingLinks.push({
      parkingFloor: floor,
      slotNumber,
      parkingFlatId,
      residentialFlatId: booking.flatId,
      residentialFlatNumber,
    });

    await expectResidentialFlatShowsParkingLink(page, booking.flatId, floor, slotNumber);
  }
}

export async function expectPartnerBookableFlatCount(
  page: Page,
  buildingId: string,
  expectedBookable: number,
) {
  await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
  const grid = await waitForMainPanel(page);
  await expect(grid.locator('#flat-grid')).toBeVisible();
  const bookable = grid.locator(
    '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"][data-bookable="true"]',
  );
  await expect(bookable).toHaveCount(expectedBookable);
}

export async function assignFlatToPartner(page: Page, flatId: string, partnerName: string) {
  const modal = await openFlatDetailsForFlat(page, flatId);
  const partnerSelect = modal.locator('#admin-partner');
  await expect(partnerSelect.getByRole('option', { name: partnerName })).toHaveCount(1, {
    timeout: 15_000,
  });
  await partnerSelect.selectOption({ label: partnerName });

  const saveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/flats/') &&
      response.url().includes('/partner') &&
      response.ok(),
    { timeout: 30_000 },
  );
  await modal.locator('#admin-partner-save').click();
  await saveResponse;

  const card = page.locator(`#flat-${flatId}`);
  await expect(card).toContainText(partnerName);

  await modal.locator('.btn-close').click();
  await expect(modal).toBeHidden();
}

function shuffle<T>(items: T[]): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

async function addPartnerToProject(
  page: Page,
  projectId: string,
  user: NewUserInput,
  buildingId: string,
) {
  await page.goto(`admin/projects/${projectId}/staff/assign`, { waitUntil: 'commit' });
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: /Add partner/ })).toBeVisible();

  await form.locator('#user-id').selectOption({ label: `${user.fullName} (${user.email})` });
  await form.locator('#assign-role').selectOption('EXECUTIVE');

  const buildingSelect = form.locator('#layout-ids-select');
  if (await buildingSelect.isVisible()) {
    await buildingSelect.selectOption(buildingId);
  }

  await Promise.all([
    page.waitForURL(new RegExp(`/admin/projects/${projectId}/staff`), { timeout: 20_000 }),
    form.getByRole('button', { name: 'Add partner' }).click(),
  ]);

  const list = await waitForMainPanel(page);
  await expect(list.locator('.alert-success').filter({ hasText: 'Partner added' }).first()).toBeVisible();
}
