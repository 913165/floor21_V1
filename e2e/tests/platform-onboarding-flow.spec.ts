import { test, expect, type Page } from '@playwright/test';
import { login, loginAsSuperAdmin } from '../helpers/auth';
import {
  buildingRow,
  createBuilding,
  openAllBuildingsList,
  sampleBuildingData,
} from '../helpers/buildings';
import {
  createProject,
  uniqueProjectName,
  waitForMainPanel,
} from '../helpers/projects';
import { createUser, sampleUserData, type NewUserInput } from '../helpers/users';

/**
 * End-to-end onboarding flow: project → users → building → partners.
 * Inserts real rows (timestamped). Clean up from Admin if needed.
 */
test.describe('Platform — full onboarding flow', () => {
  test.describe.configure({ timeout: 180_000 });

  test('creates project, two users, one building, and assigns random flats to both partners', async ({ page }) => {
    const stamp = Date.now();
    const projectName = uniqueProjectName('E2E Flow');
    const building = {
      ...sampleBuildingData(1),
      name: `E2E Flow Tower ${stamp}`,
      city: 'Mumbai',
      address: `E2E Flow Address, Andheri ${stamp}`,
    };
    const user1: NewUserInput = {
      ...sampleUserData(1),
      fullName: 'Aarav Sharma',
      email: `aarav.sharma.${stamp}@example.test`,
    };
    const user2: NewUserInput = {
      ...sampleUserData(2),
      fullName: 'Priya Nair',
      email: `priya.nair.${stamp}@example.test`,
    };

    await loginAsSuperAdmin(page);

    const { projectId } = await createProject(page, {
      name: projectName,
      city: 'Mumbai',
      address: `E2E Flow Project Address ${stamp}`,
    });

    await createUser(page, user1);
    await createUser(page, user2);

    await createBuilding(page, building, projectId);
    const buildingId = page.url().match(/\/buildings\/([0-9a-f-]+)\/flats/i)?.[1];
    if (!buildingId) {
      throw new Error(`Could not parse building id from ${page.url()}`);
    }

    const buildingsList = await openAllBuildingsList(page);
    const buildingRowEl = buildingRow(buildingsList, building.name);
    await expect(buildingRowEl).toBeVisible();
    await expect(buildingRowEl).toContainText(projectName);
    await expect(buildingRowEl).toContainText(building.city!);

    await addPartnerToProject(page, projectId, user1, buildingId);
    await addPartnerToProject(page, projectId, user2, buildingId);

    await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
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
    const assignToUser1 = shuffled.slice(0, 2);
    const assignToUser2 = shuffled.slice(2, 4);

    for (const flatId of assignToUser1) {
      await assignFlatToPartner(page, flatId, user1.fullName);
    }
    for (const flatId of assignToUser2) {
      await assignFlatToPartner(page, flatId, user2.fullName);
    }

    await page.goto(`admin/projects/${projectId}/staff`, { waitUntil: 'commit' });
    const partners = await waitForMainPanel(page);
    await expect(partners.getByRole('heading', { name: /Partners|Staff/i })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user1.email })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user2.email })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user1.email })).toContainText(building.name);
    await expect(partners.locator('tbody tr').filter({ hasText: user2.email })).toContainText(building.name);

    await logoutCurrentUser(page);
    await login(page, user1.email, user1.password);
    await expect(page).toHaveURL(/\/dashboard/);

    await page.goto(`buildings/${buildingId}/flats`, { waitUntil: 'commit' });
    const partnerGrid = await waitForMainPanel(page);
    await expect(partnerGrid.locator('#flat-grid')).toBeVisible();

    const visibleCards = partnerGrid.locator(
      '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"]',
    );
    const visibleCount = await visibleCards.count();
    expect(visibleCount).toBeGreaterThanOrEqual(4);

    const accessibleCards = partnerGrid.locator(
      '#flat-grid [data-flat-id][data-amenity="false"][data-parking="false"][data-bookable="true"]',
    );
    await expect(accessibleCards).toHaveCount(assignToUser1.length);

  });
});

async function assignFlatToPartner(page: Page, flatId: string, partnerName: string) {
  const card = page.locator(`#flat-${flatId}`);
  const detailsBtn = card.locator('.flat-quick-link');
  await detailsBtn.scrollIntoViewIfNeeded();
  await detailsBtn.click();

  const modal = page.locator('#flat-details-modal');
  await expect(modal).toBeVisible();
  await modal.locator('#admin-partner').selectOption({ label: partnerName });
  await modal.locator('#admin-partner-save').click();
  await expect(card).toContainText(partnerName);

  // Close modal before assigning the next flat; open modal/backdrop blocks grid clicks.
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

async function logoutCurrentUser(page: Page) {
  const profileMenu = page.locator('#profileMenu');
  await profileMenu.click();
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login(\?|$)/);
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
