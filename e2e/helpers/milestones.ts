import fs from 'fs';
import os from 'os';
import path from 'path';
import { expect, type Page } from '@playwright/test';
import { loginAsSuperAdmin } from './auth';
import { loadMilestoneSetupForBooking, openMilestoneTemplates, waitForUrlBookingId } from './nav';
import type { PlatformFlowState } from './platform-flow';
import { waitForMainPanel } from './projects';

/** Super admin imports the standard milestone template Excel for the E2E building. */
export async function adminImportMilestoneTemplates(page: Page, flow: PlatformFlowState) {
  await loginAsSuperAdmin(page);
  const templateResponse = await page.request.get('admin/builder-pricing-slabs/import-template');
  expect(templateResponse.ok()).toBeTruthy();
  const templatePath = path.join(os.tmpdir(), `floor21-milestones-${flow.stamp}.xlsx`);
  fs.writeFileSync(templatePath, await templateResponse.body());

  const panel = await openMilestoneTemplates(page, {
    projectId: flow.projectId,
    buildingId: flow.buildingId,
  });
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

/** Set every slab due date in the milestone grid (bulk UI + direct DOM for reliability). */
async function setSlabDueDates(page: Page, bulkDateValue: string) {
  const bulkDate = page.locator('#msBulkDate');
  if (await bulkDate.count()) {
    await bulkDate.scrollIntoViewIfNeeded();
    await bulkDate.fill(bulkDateValue);
    await page.locator('#msBulkApply').click();
  }

  await page.evaluate((value) => {
    const root = document.getElementById('floor21-main') ?? document;
    root.querySelectorAll('#milestoneSlabTable tbody tr .js-ms-due-date').forEach((el) => {
      const input = el as HTMLInputElement;
      input.readOnly = false;
      input.removeAttribute('readonly');
      input.value = value;
    });
  }, bulkDateValue);
}

/** Materialize and save slab dates for one booked client. Caller must already be logged in as that partner. */
export async function ensureClientMilestoneSchedule(
  page: Page,
  flow: PlatformFlowState,
  clientDisplayName: string,
) {
  const bookingId = bookingIdForClient(flow, clientDisplayName);
  let panel = await loadMilestoneSetupForBooking(page, flow.buildingId, bookingId);

  const slabRows = panel.locator('#milestoneSlabTable tbody tr');
  if ((await slabRows.count()) === 0) {
    const materialize = panel
      .getByRole('button', { name: 'Set Slab as per Regular Payment' })
      .first();
    if (await materialize.isVisible()) {
      page.once('dialog', (dialog) => dialog.accept());
      await Promise.all([
        waitForUrlBookingId(page, bookingId),
        materialize.click(),
      ]);
      panel = await waitForMainPanel(page);
    }
  }

  await expect(panel.locator('#milestoneSlabTable tbody tr').first()).toBeVisible({ timeout: 15_000 });

  const saveSchedule = panel.getByRole('button', { name: 'Save schedule' });
  if (await saveSchedule.isVisible()) {
    await setSlabDueDates(page, '2026-03-28');
    await saveSchedule.scrollIntoViewIfNeeded();
    await Promise.all([
      page.waitForURL(/milestone-setup.*bookingId=/, { timeout: 30_000 }),
      saveSchedule.click(),
    ]);
    panel = await waitForMainPanel(page);
    await expect(
      panel.locator('.alert-success').filter({ hasText: /Milestone schedule saved/ }).first(),
    ).toBeVisible({ timeout: 15_000 });
  }

  const rowCount = await panel.locator('#milestoneSlabTable tbody tr').count();
  expect(rowCount).toBeGreaterThan(0);
}
