import { expect, test } from '@playwright/test';
import { login } from '../helpers/auth';
import { createClient, sampleClientData } from '../helpers/clients';
import {
  resolveClientCountInteractive,
  resolveLoginCredentials,
} from '../helpers/interactive-login';
import { openClientsList } from '../helpers/nav';

test.describe.configure({ mode: 'serial' });

test('interactive — create clients for selected login', async ({ page }) => {
  test.setTimeout(300_000);

  const credentials = await resolveLoginCredentials(page);
  const count = await resolveClientCountInteractive(page);

  console.log(`\nSigning in as ${credentials.email} …`);
  console.log(`Creating ${count} client(s) …\n`);

  await login(page, credentials.email, credentials.password);

  const stamp = Date.now();
  const created: string[] = [];

  for (let index = 1; index <= count; index += 1) {
    const client = sampleClientData(stamp, index);
    const displayName = `${client.firstName} ${client.lastName}`.trim();
    console.log(`  [${index}/${count}] ${displayName}`);
    await createClient(page, client);
    created.push(displayName);
  }

  const list = await openClientsList(page);
  for (const name of created) {
    await expect(list.locator('tbody tr').filter({ hasText: name }).first()).toBeVisible();
  }

  console.log(`\nDone — ${count} client(s) created for ${credentials.email}:\n`);
  created.forEach((name, index) => {
    console.log(`  ${index + 1}. ${name}`);
  });
});
