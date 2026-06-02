import { test, expect } from '@playwright/test';
import { loginAsSuperAdmin } from '../helpers/auth';
import {
  fillNewProjectForm,
  openNewProjectForm,
  openProjectsList,
  submitProjectForm,
  uniqueProjectName,
  waitForMainPanel,
} from '../helpers/projects';

/**
 * These tests create real rows in the `builders` table (tenant projects).
 * Names are timestamped to avoid clashes; remove test projects from Admin → Projects if needed.
 */
test.describe('Platform — Projects', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSuperAdmin(page);
  });

  test('opens new project form from projects list', async ({ page }) => {
    const main = await openNewProjectForm(page);

    await expect(main.getByRole('heading', { name: 'New project' })).toBeVisible();
    await expect(main.locator('#companyName')).toBeVisible();
    await expect(main.locator('#city')).toBeVisible();
    await expect(main.locator('#address')).toBeVisible();
    await expect(main.locator('input[type=checkbox][name="active"]')).toBeChecked();
    await expect(main.getByRole('button', { name: 'Save' })).toBeVisible();
  });

  test('creates a new project and shows it in the list', async ({ page }) => {
    const projectName = uniqueProjectName();
    const city = 'Mumbai';

    const main = await openNewProjectForm(page);
    await fillNewProjectForm(main, {
      name: projectName,
      city,
      address: 'E2E test address, Andheri',
    });
    await submitProjectForm(main);

    const list = await waitForMainPanel(page);
    await expect(page).toHaveURL(/\/admin\/projects/);
    await expect(list.getByText('Project saved')).toBeVisible();
    await expect(list.locator('tbody tr').filter({ hasText: projectName })).toBeVisible();
    await expect(list.locator('tbody tr').filter({ hasText: projectName })).toContainText(city);
  });

  test('rejects whitespace-only project name', async ({ page }) => {
    await page.goto('admin/projects/new');
    const main = await waitForMainPanel(page);

    await main.locator('#companyName').fill('   ');
    await submitProjectForm(main);

    await expect(main.getByRole('heading', { name: 'New project' })).toBeVisible();
    await expect(main.locator('.alert-danger').filter({ hasText: 'Project name is required.' }).first()).toBeVisible();
  });

  test('creates an inactive project', async ({ page }) => {
    const projectName = uniqueProjectName('E2E Inactive');

    await page.goto('admin/projects/new');
    const main = await waitForMainPanel(page);
    await fillNewProjectForm(main, { name: projectName, city: 'Pune', active: false });
    await submitProjectForm(main);

    const list = await openProjectsList(page);
    const row = list.locator('tbody tr').filter({ hasText: projectName });
    await expect(row).toBeVisible();
    await expect(row.locator('.badge').filter({ hasText: 'No' })).toBeVisible();
  });
});
