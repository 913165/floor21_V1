import { expect, type Locator, type Page } from '@playwright/test';
import { waitForMainPanel } from './projects';

export type NewUserInput = {
  fullName: string;
  companyName: string;
  email: string;
  password: string;
  pan: string;
  tan: string;
  gst: string;
  mobile: string;
  address: string;
  state: string;
  pin: string;
  active?: boolean;
};

export function sampleUserData(index: number): NewUserInput {
  const stamp = Date.now();
  const digits = String((stamp + index) % 10000).padStart(4, '0');
  const pan = `ABCDE${digits}F`;
  const tan = `MUMB${String(10000 + index).padStart(5, '0').slice(-5)}B`;
  const gst = `27${pan}1Z5`;
  const mobile = `9876${String(543210 + index).padStart(6, '0').slice(-6)}`;

  return {
    fullName: `E2E User ${stamp}-${index}`,
    companyName: `E2E Company ${stamp}-${index}`,
    email: `e2e.user.${stamp}.${index}@example.com`,
    password: 'e2e123456',
    pan,
    tan,
    gst,
    mobile,
    address: `E2E Test Address ${index}, Andheri East`,
    state: 'Maharashtra',
    pin: '400001',
    active: true,
  };
}

export async function openUsersList(page: Page): Promise<Locator> {
  await page.locator('#floor21-sidebar').getByRole('link', { name: 'User Management' }).click();
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'Users' })).toBeVisible();
  return main;
}

export async function openNewUserForm(page: Page): Promise<Locator> {
  const main = await openUsersList(page);
  await main.getByRole('link', { name: 'New user' }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New user' })).toBeVisible();
  return form;
}

export async function fillNewUserForm(main: Locator, user: NewUserInput) {
  await main.locator('#user-full-name').fill(user.fullName);
  await main.locator('#user-company-name').fill(user.companyName);
  await main.locator('#user-email').fill(user.email);
  await main.locator('#user-password').fill(user.password);
  await main.locator('#user-pan').fill(user.pan);
  await main.locator('#user-tan').fill(user.tan);
  await main.locator('#user-gst').fill(user.gst);
  await main.locator('#user-mobile').fill(user.mobile);
  await main.locator('#user-address-line').fill(user.address);
  await main.locator('#user-address-state').selectOption(user.state);
  await main.locator('#user-address-pin').fill(user.pin);

  const active = main.locator('input[type=checkbox][name="active"]');
  if (user.active === false) {
    await active.uncheck();
  } else if (user.active === true) {
    await active.check();
  }
}

export async function submitNewUserForm(main: Locator, page: Page) {
  await Promise.all([
    page.waitForURL(/\/admin\/users(?:\?|$)/, { timeout: 20_000 }),
    main.getByRole('button', { name: 'Save user' }).click(),
  ]);
}

export async function createUser(page: Page, user: NewUserInput): Promise<Locator> {
  await page.goto('admin/users/new');
  const form = await waitForMainPanel(page);
  await fillNewUserForm(form, user);
  await submitNewUserForm(form, page);
  const list = await waitForMainPanel(page);
  await expect(list.getByRole('heading', { name: 'Users' })).toBeVisible({ timeout: 15_000 });
  await expect(list.locator('.alert-success').filter({ hasText: 'User saved.' }).first()).toBeVisible();
  return list;
}

export function userRow(list: Locator, user: NewUserInput) {
  return list.locator('tbody tr').filter({ hasText: user.email });
}
