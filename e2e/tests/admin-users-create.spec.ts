import { test, expect } from '@playwright/test';
import { loginAsSuperAdmin } from '../helpers/auth';
import { waitForMainPanel } from '../helpers/projects';
import {
  createUser,
  fillNewUserForm,
  openNewUserForm,
  openUsersList,
  sampleUserData,
  submitNewUserForm,
  userRow,
} from '../helpers/users';

/**
 * These tests insert real rows in the `users` table (timestamped emails).
 * Remove test users from Admin → User Management if needed.
 */
test.describe('Platform — User Management (create)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsSuperAdmin(page);
  });

  test('creates one user and shows it in the list', async ({ page }) => {
    const user = sampleUserData(1);
    const list = await createUser(page, user);

    const row = userRow(list, user);
    await expect(row).toBeVisible();
    await expect(row).toContainText(user.fullName);
    await expect(row).toContainText(user.companyName);
  });

  test('creates five users in sequence', async ({ page }) => {
    const users = [1, 2, 3, 4, 5].map((i) => sampleUserData(i));

    for (const user of users) {
      const list = await createUser(page, user);
      await expect(userRow(list, user)).toBeVisible();
    }

    const list = await openUsersList(page);
    for (const user of users) {
      await expect(userRow(list, user)).toBeVisible();
      await expect(userRow(list, user)).toContainText(user.companyName);
    }
  });

  test('creates user from New user button on list page', async ({ page }) => {
    const user = sampleUserData(10);
    const form = await openNewUserForm(page);
    await fillNewUserForm(form, user);
    await submitNewUserForm(form, page);

    const list = await waitForMainPanel(page);
    await expect(list.locator('.alert-success').filter({ hasText: 'User saved.' }).first()).toBeVisible();
    await expect(userRow(list, user)).toBeVisible();
  });

  test('creates inactive user', async ({ page }) => {
    const user = { ...sampleUserData(11), active: false };
    const list = await createUser(page, user);

    const row = userRow(list, user);
    await expect(row).toBeVisible();
    await expect(row.locator('.badge').filter({ hasText: 'No' })).toBeVisible();
  });

  test('rejects duplicate email on second save', async ({ page }) => {
    const user = sampleUserData(12);
    await createUser(page, user);

    await page.goto('admin/users/new');
    const form = await waitForMainPanel(page);
    await fillNewUserForm(form, {
      ...sampleUserData(99),
      email: user.email,
    });
    await form.getByRole('button', { name: 'Save user' }).click();

    await expect(form.getByRole('heading', { name: 'New user' })).toBeVisible();
    await expect(form.locator('.alert-danger').filter({ hasText: 'Email is already used' }).first()).toBeVisible();
  });
});
