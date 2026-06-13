import { expect, type Page } from '@playwright/test';

/** Seeded in Flyway (see QUICKSTART.md). */
export const SUPER_ADMIN = {
  email: 'super@floor21.com',
  password: 'super123',
} as const;

export const BUILDER_ADMIN = {
  /** Legacy demo tenant removed by V3 migration; create a project + partner user for tenant tests. */
  email: 'admin@skylinehomes.com',
  password: 'admin123',
} as const;

/** Main content turbo-frame used by layout/base.html (not an iframe). */
export function mainPanel(page: Page) {
  return page.locator('#floor21-main');
}

/** @deprecated use mainPanel — turbo-frame is not an iframe */
export function mainFrame(page: Page) {
  return mainPanel(page);
}

/** End the current session so a different user can sign in. */
export async function logoutIfAuthenticated(page: Page): Promise<void> {
  await page.goto('dashboard', { waitUntil: 'commit' });
  if (page.url().includes('/login')) {
    return;
  }
  const profileMenu = page.locator('#profileMenu');
  if (!(await profileMenu.isVisible())) {
    return;
  }
  await profileMenu.click();
  const logout = page.locator('form[action$="/logout"] button[type="submit"]');
  await expect(logout).toBeVisible({ timeout: 10_000 });
  await Promise.all([
    page.waitForURL(/\/login/, { timeout: 30_000 }),
    logout.click(),
  ]);
}

export async function login(page: Page, email: string, password: string) {
  await logoutIfAuthenticated(page);
  if (!page.url().includes('/login')) {
    await page.goto('login', { waitUntil: 'commit' });
  }
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await Promise.all([
    page.waitForURL(/\/dashboard/, { timeout: 30_000 }),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ]);
}

export async function loginAsSuperAdmin(page: Page) {
  await login(page, SUPER_ADMIN.email, SUPER_ADMIN.password);
}

/** Reuse an existing super-admin session when already signed in. */
export async function ensureSuperAdmin(page: Page) {
  await page.goto('dashboard', { waitUntil: 'commit' });
  if (page.url().includes('/login')) {
    await loginAsSuperAdmin(page);
  } else {
    await expect(page).toHaveURL(/\/dashboard/);
  }
}
