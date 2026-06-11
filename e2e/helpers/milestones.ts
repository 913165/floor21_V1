import fs from 'fs';
import os from 'os';
import path from 'path';
import { expect, type Page } from '@playwright/test';
import { loginAsSuperAdmin } from './auth';
import type { PlatformFlowState } from './platform-flow';
import { waitForMainPanel } from './projects';

/** Super admin imports the standard milestone template Excel for the E2E building. */
export async function adminImportMilestoneTemplates(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  const templateResponse = await page.request.get('admin/builder-pricing-slabs/import-template');
  expect(templateResponse.ok()).toBeTruthy();
  const templatePath = path.join(os.tmpdir(), `floor21-milestones-${flow.stamp}.xlsx`);
  fs.writeFileSync(templatePath, await templateResponse.body());

  await page.goto(
    `admin/builder-pricing-slabs?projectId=${flow.projectId}&buildingId=${flow.buildingId}`,
    { waitUntil: 'commit' },
  );
  const panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'Milestone Templates' })).toBeVisible();

  const importForm = panel.locator('#slabs-import-form');
  await expect(importForm).toBeVisible();
  await panel.locator('#replaceSlabs').check();
  await panel.locator('#slabFile').setInputFiles(templatePath);
  await Promise.all([
    page.waitForURL(/buildingId=/, { timeout: 30_000 }),
    importForm.getByRole('button', { name: 'Import' }).click(),
  ]);

  const after = await waitForMainPanel(page);
  await expect(
    after.locator('.alert-success').filter({ hasText: /Imported \d+ rate slab/ }).first(),
  ).toBeVisible();
}

function bookingIdForClient(flow: PlatformFlowState, clientDisplayName: string): string {
  const booking = flow.bookings?.find((b) => b.clientDisplayName === clientDisplayName);
  if (!booking?.bookingId) {
    throw new Error(`No booking in flow state for client: ${clientDisplayName}`);
  }
  return booking.bookingId;
}

/** Materialize and save slab dates for one booked client. Caller must already be logged in as that partner. */
export async function ensureClientMilestoneSchedule(
  page: Page,
  flow: PlatformFlowState,
  clientDisplayName: string,
) {
  const bookingId = bookingIdForClient(flow, clientDisplayName);
  await page.goto(
    `clients/milestone-setup?buildingId=${flow.buildingId}&bookingId=${bookingId}`,
    { waitUntil: 'commit' },
  );
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: /Milestone setup/i })).toBeVisible();

  const slabRows = panel.locator('#milestoneSlabTable tbody tr');
  if ((await slabRows.count()) === 0) {
    const materialize = panel
      .getByRole('button', { name: 'Set Slab as per Regular Payment' })
      .first();
    if (await materialize.isVisible()) {
      page.once('dialog', (dialog) => dialog.accept());
      await Promise.all([
        page.waitForURL(/milestone-setup.*bookingId=/, { timeout: 30_000 }),
        materialize.click(),
      ]);
      panel = await waitForMainPanel(page);
    }
  }

  const bulkDate = panel.locator('#msBulkDate');
  if (await bulkDate.isVisible()) {
    await bulkDate.fill('2026-03-28');
    await panel.locator('#msBulkApply').click();
  }

  const saveSchedule = panel.getByRole('button', { name: 'Save schedule' });
  if (await saveSchedule.isVisible()) {
    await Promise.all([
      page.waitForURL(/milestone-setup.*bookingId=/, { timeout: 30_000 }),
      saveSchedule.click(),
    ]);
    panel = await waitForMainPanel(page);
  }

  await expect(panel.locator('#milestoneSlabTable tbody tr').first()).toBeVisible();
}
