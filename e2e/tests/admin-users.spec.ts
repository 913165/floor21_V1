import { test, expect } from '@playwright/test';
import { loginAsSuperAdmin, mainFrame } from '../helpers/auth';

test.describe('Platform — User Management', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSuperAdmin(page);
  });

  test('opens users list from sidebar', async ({ page }) => {
    await page
      .locator('#floor21-sidebar')
      .getByRole('link', { name: 'User Management' })
      .click();

    const main = mainFrame(page);
    await expect(main.getByRole('heading', { name: 'Users' })).toBeVisible();
    await expect(main.getByRole('link', { name: 'New user' })).toBeVisible();
  });

  test('new user form loads', async ({ page }) => {
    await page.goto('/admin/users/new');

    const main = mainFrame(page);
    await expect(main.locator('#user-full-name')).toBeVisible();
    await expect(main.locator('#user-email')).toBeVisible();
    await expect(main.locator('#user-password')).toBeVisible();
  });
});
