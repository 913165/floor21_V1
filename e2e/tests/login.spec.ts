import { test, expect } from '@playwright/test';
import { SUPER_ADMIN } from '../helpers/auth';

test.describe('Login', () => {
  test('shows sign-in form', async ({ page }) => {
    await page.goto('login');
    await expect(page.locator('.floor21-login__card-title')).toHaveText('Sign in');
    await expect(page.locator('#login-email')).toBeVisible();
    await expect(page.locator('#login-password')).toHaveAttribute('type', 'password');
  });

  test('super admin reaches dashboard', async ({ page }) => {
    await page.goto('login');
    await page.locator('#login-email').fill(SUPER_ADMIN.email);
    await page.locator('#login-password').fill(SUPER_ADMIN.password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('#floor21-sidebar')).toBeVisible();
  });

  test('rejects invalid credentials', async ({ page }) => {
    await page.goto('login');
    await page.locator('#login-email').fill('nobody@example.com');
    await page.locator('#login-password').fill('wrong');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/login\?error=true/);
    await expect(page.getByText('Invalid email or password.')).toBeVisible();
  });
});
