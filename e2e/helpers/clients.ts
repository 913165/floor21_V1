import { expect, type Page } from '@playwright/test';
import { waitForMainPanel } from './projects';

export type NewClientInput = {
  firstName: string;
  lastName: string;
  address1: string;
  city: string;
  mobile1: string;
  panNumber: string;
  aadhaarNumber: string;
  dob: string;
  email1?: string;
};

/** Fixed demo buyer for receipts / payment-schedule screenshots (partner 1, first client). */
export const E2E_PRIMARY_BUYER = {
  firstName: 'Client1',
  lastName: 'Buyer',
  email: 'client1@example.com',
} as const;

export function primaryBuyerClientData(): NewClientInput {
  return {
    firstName: E2E_PRIMARY_BUYER.firstName,
    lastName: E2E_PRIMARY_BUYER.lastName,
    email1: E2E_PRIMARY_BUYER.email,
    address1: '101 Demo Heights, Andheri West',
    city: 'Mumbai',
    mobile1: '9876543210',
    panNumber: 'ABCDE1234F',
    aadhaarNumber: '123456789012',
    dob: '1990-01-15',
  };
}

export function sampleClientData(stamp: number, index = 1): NewClientInput {
  const digits = String((stamp + index) % 10000).padStart(4, '0');
  return {
    firstName: `E2E Client ${stamp}`,
    lastName: 'Buyer',
    address1: `E2E Client Address ${index}, Andheri`,
    city: 'Mumbai',
    mobile1: `9876${String(543210 + index).padStart(6, '0').slice(-6)}`,
    panNumber: `ABCDE${digits}F`,
    aadhaarNumber: `${String(100000000000 + index).slice(0, 12)}`,
    dob: '1990-06-15',
    email1: `e2e.client.${stamp}.${index}@example.test`,
  };
}

export async function createClient(page: Page, client: NewClientInput): Promise<void> {
  await page.goto('clients/new', { waitUntil: 'commit' });
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New Client' })).toBeVisible();

  await form.locator('#firstName').fill(client.firstName);
  await form.locator('#lastName').fill(client.lastName);
  await form.locator('#address1').fill(client.address1);
  await form.locator('#city').fill(client.city);
  await form.locator('#mobile1').fill(client.mobile1);
  await form.locator('#panNumber').fill(client.panNumber);
  await form.locator('#aadhaarNumber').fill(client.aadhaarNumber);
  await form.locator('#dob').fill(client.dob);
  if (client.email1) {
    await form.locator('#email1').fill(client.email1);
  }

  await Promise.all([
    page.waitForURL(/\/clients(?:\?|$)/, { timeout: 20_000 }),
    form.getByRole('button', { name: 'Save' }).click(),
  ]);

  const list = await waitForMainPanel(page);
  await expect(list.locator('.alert-success').filter({ hasText: 'Client saved' }).first()).toBeVisible();
}
