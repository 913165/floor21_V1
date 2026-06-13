import { expect, type Locator, type Page } from '@playwright/test';
import { openNewUserForm, openUsersList } from './nav';
import { waitForMainPanel } from './projects';

/** Password for all partner users created by the E2E flow (easy to remember for manual checks). */
export const E2E_USER_PASSWORD = 'user123';

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
    password: E2E_USER_PASSWORD,
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

export { openNewUserForm, openUsersList } from './nav';

export async function fillNewUserForm(main: Locator, user: NewUserInput) {
  await main.locator('#user-full-name').fill(user.fullName);
  await main.locator('#user-company-name').fill(user.companyName);
  await main.locator('#user-email').fill(user.email);
  const password = main.locator('#user-password');
  await password.click();
  await password.fill(user.password);
  await expect(password).toHaveValue(user.password);
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
  const form = await openNewUserForm(page);
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

/** Pick a user on Add owner/partner (server-backed search combobox). */
export async function selectAssignableUser(form: Locator, user: NewUserInput) {
  const search = form.locator('#user-id-search');
  await expect(search).toBeVisible();
  await search.click();
  await search.fill(user.email);
  const option = form.locator('.project-search-select__option').filter({ hasText: user.email });
  await expect(option.first()).toBeVisible({ timeout: 15_000 });
  await option.first().click();
  await expect(form.locator('#user-id')).not.toHaveValue('');
}
