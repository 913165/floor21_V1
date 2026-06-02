import { test, expect, type Page } from '@playwright/test';
import { loginAsSuperAdmin } from '../helpers/auth';
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

  test('creates project, two users, one building, and adds both as partners', async ({ page }) => {
    const stamp = Date.now();
    const projectName = uniqueProjectName('E2E Flow');
    const building = {
      ...sampleBuildingData(1),
      name: `E2E Flow Tower ${stamp}`,
      city: 'Mumbai',
      address: `E2E Flow Address, Andheri ${stamp}`,
    };
    const user1 = sampleUserData(1);
    const user2 = sampleUserData(2);

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

    await page.goto(`admin/projects/${projectId}/staff`, { waitUntil: 'commit' });
    const partners = await waitForMainPanel(page);
    await expect(partners.getByRole('heading', { name: /Partners|Staff/i })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user1.email })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user2.email })).toBeVisible();
    await expect(partners.locator('tbody tr').filter({ hasText: user1.email })).toContainText(building.name);
    await expect(partners.locator('tbody tr').filter({ hasText: user2.email })).toContainText(building.name);
  });
});

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
