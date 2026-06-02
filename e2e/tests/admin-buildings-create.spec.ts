import { test, expect } from '@playwright/test';
import { loginAsSuperAdmin } from '../helpers/auth';
import {
  buildingRow,
  createBuilding,
  fillNewBuildingForm,
  firstTenantProjectId,
  openAllBuildingsList,
  openNewBuildingForm,
  sampleBuildingData,
} from '../helpers/buildings';

/**
 * Inserts real rows in `buildings` and generated flats. Names are timestamped.
 * Remove test buildings from Admin → All buildings if needed (only when no bookings).
 */
test.describe('Platform — All buildings (create)', () => {
  test.describe.configure({ timeout: 120_000 });

  let projectId: string;

  test.beforeEach(async ({ page }) => {
    await loginAsSuperAdmin(page);
    projectId = await firstTenantProjectId(page);
  });

  test('opens add building form from All buildings', async ({ page }) => {
    const form = await openNewBuildingForm(page, projectId);

    await expect(form.locator('#buildingName')).toBeVisible();
    await expect(form.getByRole('button', { name: 'Generate building layout' })).toBeVisible();
  });

  test('creates three buildings and shows them in All buildings', async ({ page }) => {
    const buildings = [1, 2, 3].map((i) => sampleBuildingData(i));

    for (const building of buildings) {
      await createBuilding(page, building, projectId);
    }

    const list = await openAllBuildingsList(page);
    for (const building of buildings) {
      const row = buildingRow(list, building.name);
      await expect(row).toBeVisible();
      await expect(row).toContainText(building.city!);
    }
  });

  test('rejects unit mix that does not match flats per floor', async ({ page }) => {
    const form = await openNewBuildingForm(page, projectId);
    await fillNewBuildingForm(form, {
      ...sampleBuildingData(99),
      flatsPerFloor: 4,
      twoBhkPerFloor: 2,
    });
    await form.getByRole('button', { name: 'Generate building layout' }).click();

    await expect(form.getByRole('heading', { name: /Add building layout/ })).toBeVisible();
    await expect(
      form.locator('.alert-danger').filter({ hasText: 'Unit counts per floor must add up' }).first(),
    ).toBeVisible();
  });
});
