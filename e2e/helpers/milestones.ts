import fs from 'fs';
import os from 'os';
import path from 'path';
import { expect, type Page } from '@playwright/test';
import { loginAsSuperAdmin } from './auth';
import { loadPaymentScheduleForBooking, openMilestoneTemplates } from './nav';
import type { PlatformFlowState } from './platform-flow';
import { waitForMainPanel } from './projects';

/** Aligns with e2e booking date in helpers/bookings.ts (slab dates clamp to booking date). */
export const E2E_MILESTONE_TEMPLATE_DUE_DATE = '2026-06-15';

/** Super admin imports milestone templates and saves centralized due dates for the E2E building. */
export async function adminSetupMilestoneTemplates(page: Page, flow: PlatformFlowState) {
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

  let after = await waitForMainPanel(page);
  await expect(
    after.locator('.alert-success').filter({ hasText: /Imported \d+ rate slab/ }).first(),
  ).toBeVisible();

  await adminSaveCentralizedMilestoneDates(page, E2E_MILESTONE_TEMPLATE_DUE_DATE);
}

/** @deprecated use adminSetupMilestoneTemplates */
export const adminImportMilestoneTemplates = adminSetupMilestoneTemplates;

async function adminSaveCentralizedMilestoneDates(page: Page, templateDueDate: string) {
  const panel = await waitForMainPanel(page);
  await expect(panel.locator('#slabDatesForm')).toBeVisible({ timeout: 15_000 });
  await panel.locator('#milestoneTemplateDueDate').fill(templateDueDate);

  const dateInputs = panel.locator('input[name="slabDates"][form="slabDatesForm"]');
  const count = await dateInputs.count();
  for (let i = 0; i < count; i++) {
    await dateInputs.nth(i).fill(templateDueDate);
  }

  await Promise.all([
    page.waitForURL(/buildingId=/, { timeout: 30_000 }),
    panel.getByRole('button', { name: 'Save slab dates' }).click(),
  ]);

  const after = await waitForMainPanel(page);
  await expect(
    after.locator('.alert-success').filter({ hasText: /Milestone slab dates saved/ }).first(),
  ).toBeVisible();
}

function bookingIdForClient(flow: PlatformFlowState, clientDisplayName: string): string {
  const booking = flow.bookings?.find((b) => b.clientDisplayName === clientDisplayName);
  if (!booking?.bookingId) {
    throw new Error(`No booking in flow state for client: ${clientDisplayName}`);
  }
  return booking.bookingId;
}

/**
 * Opens payment schedule for a booking. Slab rows auto-materialize from centralized
 * Milestone Templates (building common date + per-row dates).
 */
export async function ensurePaymentScheduleReady(
  page: Page,
  flow: PlatformFlowState,
  clientDisplayName: string,
) {
  const bookingId = bookingIdForClient(flow, clientDisplayName);
  const schedule = await loadPaymentScheduleForBooking(page, flow.buildingId, bookingId);
  await expect(schedule.getByText('Slab payment schedule')).toBeVisible({ timeout: 30_000 });
  await expect(schedule.locator('.slab-schedule-ledger-table tbody tr').first()).toBeVisible({
    timeout: 15_000,
  });
  const rowCount = await schedule.locator('.slab-schedule-ledger-table tbody tr').count();
  expect(rowCount).toBeGreaterThan(0);
}

/** @deprecated use ensurePaymentScheduleReady */
export const ensureClientMilestoneSchedule = ensurePaymentScheduleReady;
