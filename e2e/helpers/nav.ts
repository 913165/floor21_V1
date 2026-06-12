import { expect, type Locator, type Page } from '@playwright/test';
import { waitForMainPanel } from './projects';

/** Click a sidebar link and wait for the turbo main panel to finish loading. */
export async function clickSidebarLink(page: Page, name: string | RegExp): Promise<void> {
  const link = page.locator('#floor21-sidebar').getByRole('link', { name });
  await expect(link).toBeVisible();
  await link.click();
  await waitForMainPanel(page);
}

export async function openClientsList(page: Page): Promise<Locator> {
  await clickSidebarLink(page, 'Clients');
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'Clients' })).toBeVisible();
  return main;
}

export async function openNewClientForm(page: Page): Promise<Locator> {
  const main = await openClientsList(page);
  await main.getByRole('link', { name: 'New client' }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New Client' })).toBeVisible();
  return form;
}

export async function openBookingsList(page: Page): Promise<Locator> {
  await clickSidebarLink(page, 'Bookings');
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'Bookings' })).toBeVisible();
  return main;
}

export async function openNewBookingForm(page: Page): Promise<Locator> {
  const main = await openBookingsList(page);
  await main.getByRole('link', { name: 'New booking' }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New booking' })).toBeVisible();
  return form;
}

export async function openTenantBuildingsList(page: Page): Promise<Locator> {
  await clickSidebarLink(page, 'Buildings');
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'Buildings' })).toBeVisible();
  return main;
}

export async function openBuildingFlatGrid(
  page: Page,
  options: { buildingId: string; buildingName?: string },
): Promise<Locator> {
  const main = await openTenantBuildingsList(page);
  const row = options.buildingName
    ? main.locator('tbody tr').filter({ has: main.getByRole('cell', { name: options.buildingName }) })
    : main.locator(`tbody tr[data-building-id="${options.buildingId}"]`);
  await row.first().getByRole('link', { name: 'Flats' }).click();
  const grid = await waitForMainPanel(page);
  await expect(grid.locator('#flat-grid')).toBeVisible({ timeout: 30_000 });
  return grid;
}

export async function openAllBuildingsList(
  page: Page,
  options?: { projectId?: string },
): Promise<Locator> {
  await clickSidebarLink(page, 'All buildings');
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'All buildings' })).toBeVisible();
  if (options?.projectId) {
    await main.locator('#filterProject').selectOption(options.projectId);
    await waitForMainPanel(page);
    await expect(main.locator('#filterProject')).toHaveValue(options.projectId);
  }
  return main;
}

export async function openAdminBuildingFlatGrid(
  page: Page,
  options: { buildingId: string; projectId?: string },
): Promise<Locator> {
  const main = await openAllBuildingsList(
    page,
    options.projectId ? { projectId: options.projectId } : undefined,
  );
  const row = main.locator(`tbody tr[data-building-id="${options.buildingId}"]`);
  await row.first().getByRole('link', { name: 'Flats' }).click();
  const grid = await waitForMainPanel(page);
  await expect(grid.locator('#flat-grid')).toBeVisible({ timeout: 30_000 });
  return grid;
}

export async function openNewBuildingForm(page: Page, projectId: string): Promise<Locator> {
  const main = await openAllBuildingsList(page, { projectId });
  await main.getByRole('link', { name: 'Add building' }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: /Add building layout/ })).toBeVisible();
  await expect(form.locator('#admin-builder-id')).toHaveValue(projectId);
  return form;
}

export async function openProjectsList(page: Page): Promise<Locator> {
  await clickSidebarLink(page, 'Projects');
  const main = await waitForMainPanel(page);
  await expect(main.getByRole('heading', { name: 'Projects' })).toBeVisible();
  return main;
}

export async function openNewProjectForm(page: Page): Promise<Locator> {
  const main = await openProjectsList(page);
  await main.getByRole('link', { name: 'New project' }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: 'New project' })).toBeVisible();
  return form;
}

export async function openProjectStaffAssign(
  page: Page,
  projectName: string,
): Promise<Locator> {
  const list = await openProjectsList(page);
  const row = list.locator('tbody tr').filter({ hasText: projectName });
  await row.first().getByRole('link', { name: /owner\/partner/i }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: /Add owner\/partner/i })).toBeVisible();
  return form;
}

export async function openUsersList(page: Page): Promise<Locator> {
  await clickSidebarLink(page, 'User Management');
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

export async function openMilestoneTemplates(
  page: Page,
  options: { projectId: string; buildingId: string },
): Promise<Locator> {
  await clickSidebarLink(page, 'Milestone Templates');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'Milestone Templates' })).toBeVisible();
  await panel.locator('#filterProject').selectOption(options.projectId);
  panel = await waitForMainPanel(page);
  await panel.locator('#filterBuilding').selectOption(options.buildingId);
  panel = await waitForMainPanel(page);
  return panel;
}

export async function loadMilestoneSetupForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  await clickSidebarLink(page, 'Milestone setup (Clients)');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: /Milestone setup/i })).toBeVisible();

  await panel.locator('#msBuilding').selectOption(buildingId);
  await Promise.all([
    page.waitForURL(/milestone-setup.*buildingId=/, { timeout: 30_000 }),
    panel.locator('#milestonePickerForm').getByRole('button', { name: 'Search' }).click(),
  ]);
  panel = await waitForMainPanel(page);

  await panel.locator('#msBooking').selectOption(bookingId);
  await Promise.all([
    page.waitForURL(/milestone-setup.*bookingId=/, { timeout: 30_000 }),
    panel.getByRole('button', { name: 'Load client' }).click(),
  ]);
  return waitForMainPanel(page);
}

export async function loadPaymentReceiptsForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  await clickSidebarLink(page, 'Payment Receipts');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'Payment Receipts' })).toBeVisible();

  await panel.locator('#receiptBuilding').selectOption(buildingId);
  panel = await waitForMainPanel(page);
  await panel.locator('#receiptBooking').selectOption(bookingId);
  await Promise.all([
    page.waitForURL(/receipts\?.*bookingId=/, { timeout: 30_000 }),
    panel.locator('#receiptPickerForm').getByRole('button', { name: 'Load booking' }).click(),
  ]);
  return waitForMainPanel(page);
}

export async function loadPaymentScheduleForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  await clickSidebarLink(page, 'Payment schedule (Clients)');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: /Payment schedule/i })).toBeVisible();

  await panel.locator('#scheduleBuilding').selectOption(buildingId);
  panel = await waitForMainPanel(page);
  await panel.locator('#scheduleBooking').selectOption(bookingId);
  await Promise.all([
    page.waitForURL(/payment-schedule.*bookingId=/, { timeout: 30_000 }),
    panel.locator('#schedulePickerForm').getByRole('button', { name: 'Load booking' }).click(),
  ]);
  return waitForMainPanel(page);
}
