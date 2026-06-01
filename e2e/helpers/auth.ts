import { expect, type Page } from '@playwright/test';

/** Seeded in Flyway (see QUICKSTART.md). */
export const SUPER_ADMIN = {
  email: 'super@floor21.com',
  password: 'super123',
} as const;

export const BUILDER_ADMIN = {
  email: 'admin@skylinehomes.com',
  password: 'admin123',
} as const;

/** Main content turbo-frame used by layout/base.html. */
export function mainFrame(page: Page) {
  return page.frameLocator('#floor21-main');
}

export async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

export async function loginAsSuperAdmin(page: Page) {
  await login(page, SUPER_ADMIN.email, SUPER_ADMIN.password);
  await expect(page).toHaveURL(/\/dashboard/);
}
