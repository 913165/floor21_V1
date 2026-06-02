import { expect, type Page } from '@playwright/test';
import {
  buildingRow,
  createBuilding,
  openAllBuildingsList,
  sampleBuildingData,
  type NewBuildingInput,
} from './buildings';
import { createProject, uniqueProjectName, waitForMainPanel } from './projects';
import { createUser, sampleUserData, type NewUserInput } from './users';
import { loginAsSuperAdmin } from './auth';

export type PlatformFlowState = {
  stamp: number;
  projectName: string;
  projectId: string;
  user1: NewUserInput;
  user2: NewUserInput;
  building: NewBuildingInput;
  buildingId: string;
  assignToUser1: string[];
  assignToUser2: string[];
  clientFirstName: string;
  clientLastName: string;
  clientDisplayName: string;
  bookingCode: string;
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
    assignToUser1: [],
    assignToUser2: [],
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

  const list = await openAllBuildingsList(page);
  const row = buildingRow(list, flow.building.name);
  await expect(row).toBeVisible();
  await expect(row).toContainText(flow.projectName);
}

export async function adminAddPartners(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await addPartnerToProject(page, flow.projectId, flow.user1, flow.buildingId);
  await addPartnerToProject(page, flow.projectId, flow.user2, flow.buildingId);
}

export async function adminAssignFlats(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  await page.goto(`buildings/${flow.buildingId}/flats`, { waitUntil: 'commit' });
  const adminGrid = await waitForMainPanel(page);
  await expect(adminGrid.locator('#flat-grid')).toBeVisible();

  const candidateFlats = adminGrid.locator(
    '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"]',
  );
  const candidateCount = await candidateFlats.count();
  expect(candidateCount).toBeGreaterThanOrEqual(4);

  const flatIds: string[] = [];
  for (let i = 0; i < candidateCount; i++) {
    const id = await candidateFlats.nth(i).getAttribute('data-flat-id');
    if (id) flatIds.push(id);
  }
  const shuffled = shuffle(flatIds);
  flow.assignToUser1 = shuffled.slice(0, 2);
  flow.assignToUser2 = shuffled.slice(2, 4);

  for (const flatId of flow.assignToUser1) {
    await assignFlatToPartner(page, flatId, flow.user1.fullName);
  }
  for (const flatId of flow.assignToUser2) {
    await assignFlatToPartner(page, flatId, flow.user2.fullName);
  }
}

export async function assignFlatToPartner(page: Page, flatId: string, partnerName: string) {
  const card = page.locator(`#flat-${flatId}`);
  const detailsBtn = card.locator('.flat-quick-link');
  await detailsBtn.scrollIntoViewIfNeeded();
  await detailsBtn.click();

  const modal = page.locator('#flat-details-modal');
  await expect(modal).toBeVisible();
  await modal.locator('#admin-partner').selectOption({ label: partnerName });
  await modal.locator('#admin-partner-save').click();
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
