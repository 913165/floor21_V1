import { expect, type Locator, type Page } from '@playwright/test';
import { closeClientsImportModalIfOpen } from './modals';
import { waitForMainPanel } from './projects';

export { closeClientsImportModalIfOpen };

function urlParam(url: string | URL, name: string): string | null {
  return new URL(url).searchParams.get(name);
}

function waitForUrlBookingId(page: Page, bookingId: string) {
  return page.waitForURL((url) => urlParam(url, 'bookingId') === bookingId, { timeout: 30_000 });
}

export { waitForUrlBookingId };

async function applyProjectFilter(page: Page, main: Locator, projectId: string): Promise<Locator> {
  const select = main.locator('#filterProject');
  await expect(main.locator('#filterProject-search')).toBeVisible({ timeout: 15_000 });
  if ((await select.inputValue()) === projectId && page.url().includes(`projectId=${projectId}`)) {
    return main;
  }

  const label = (await select.locator(`option[value="${projectId}"]`).textContent())?.trim();
  expect(label, `Project ${projectId} not in filter dropdown`).toBeTruthy();

  const search = main.locator('#filterProject-search');
  await search.click();
  await search.fill(label!);
  const option = main
    .locator('#filterProject-menu .project-search-select__option')
    .filter({ hasText: label! })
    .first();
  await expect(option).toBeVisible({ timeout: 15_000 });

  await Promise.all([
    page.waitForURL((url) => new URL(url).searchParams.get('projectId') === projectId, {
      timeout: 30_000,
    }),
    option.click(),
  ]);

  const filtered = await waitForMainPanel(page);
  await expect(filtered.locator('#filterProject')).toHaveValue(projectId);
  return filtered;
}

function slabsFilterUrlMatches(
  url: string,
  projectId: string,
  buildingId: string,
): boolean {
  const params = new URL(url).searchParams;
  return params.get('projectId') === projectId && params.get('buildingId') === buildingId;
}

/** Building filter: set native selects and submit once (project + building must both reach the server). */
async function applyBuildingFilter(
  page: Page,
  main: Locator,
  projectId: string,
  buildingId: string,
): Promise<Locator> {
  const form = main.locator('#slabs-filter-form');
  const projectSelect = main.locator('#filterProject');
  const buildingSelect = form.locator('#filterBuilding');
  await expect(projectSelect).toHaveValue(projectId);
  await expect(buildingSelect.locator(`option[value="${buildingId}"]`)).toHaveCount(1, {
    timeout: 15_000,
  });

  const importForm = main.locator('#slabs-import-form');
  if (
    (await projectSelect.inputValue()) === projectId &&
    (await buildingSelect.inputValue()) === buildingId &&
    (await importForm.isVisible())
  ) {
    return main;
  }

  await Promise.all([
    page.waitForURL((url) => slabsFilterUrlMatches(url, projectId, buildingId), {
      timeout: 30_000,
    }),
    form.evaluate(
      (formEl, ids) => {
        const project = formEl.querySelector('#filterProject') as HTMLSelectElement | null;
        const building = formEl.querySelector('#filterBuilding') as HTMLSelectElement | null;
        if (project) {
          project.value = ids.projectId;
        }
        if (building) {
          building.value = ids.buildingId;
        }
        if (typeof formEl.requestSubmit === 'function') {
          formEl.requestSubmit();
        } else {
          formEl.submit();
        }
      },
      { projectId, buildingId },
    ),
  ]);

  const filtered = await waitForMainPanel(page);
  await expect(filtered.locator('#filterProject')).toHaveValue(projectId);
  await expect(filtered.locator('#filterBuilding')).toHaveValue(buildingId);
  await expect(filtered.locator('#slabs-import-form')).toBeVisible({ timeout: 15_000 });
  return filtered;
}

/** Click a sidebar link and wait for the turbo main panel to finish loading. */
export async function clickSidebarLink(page: Page, name: string | RegExp): Promise<void> {
  const sidebar = page.locator('#floor21-sidebar');
  const link =
    typeof name === 'string'
      ? sidebar.getByRole('link', { name, exact: true })
      : sidebar.getByRole('link', { name });
  await expect(link).toBeVisible();
  await link.click();
  await waitForMainPanel(page);
}

export async function openClientsList(page: Page): Promise<Locator> {
  await closeClientsImportModalIfOpen(page);
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
    return applyProjectFilter(page, main, options.projectId);
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
  const addBuilding = main.getByRole('link', { name: 'Add building' });
  await expect(addBuilding).toHaveAttribute('href', new RegExp(`builderId=${projectId}`));
  await addBuilding.click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: /Add building layout/ })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`builderId=${projectId}`));
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
  const staffList = await waitForMainPanel(page);
  await expect(staffList.getByRole('heading', { name: /owner\/partner list/i })).toBeVisible();
  await staffList.getByRole('link', { name: /add owner\/partner/i }).click();
  const form = await waitForMainPanel(page);
  await expect(form.getByRole('heading', { name: /add owner\/partner/i })).toBeVisible();
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
  const panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'New user' })).toBeVisible();
  const userForm = panel.locator('form[action*="/admin/users/save"]').last();
  await expect(userForm).toBeVisible();
  return userForm;
}

export async function openMilestoneTemplates(
  page: Page,
  options: { projectId: string; buildingId: string },
): Promise<Locator> {
  await clickSidebarLink(page, 'Milestone Templates');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'Milestone Templates' })).toBeVisible();
  panel = await applyProjectFilter(page, panel, options.projectId);
  return applyBuildingFilter(page, panel, options.projectId, options.buildingId);
}

export async function loadMilestoneSetupForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  await clickSidebarLink(page, 'Milestone setup (Clients)');
  let panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: /Milestone setup/i })).toBeVisible();

  // Search is th:disabled until buildingId is already in the URL — selectOption alone cannot enable it.
  await panel.locator('#msBuilding').selectOption(buildingId);
  await Promise.all([
    page.waitForURL(/milestone-setup.*buildingId=/, { timeout: 30_000 }),
    panel
      .locator('#milestonePickerForm')
      .evaluate((form: HTMLFormElement) => form.requestSubmit()),
  ]);
  panel = await waitForMainPanel(page);

  await expect(panel.locator('#msBooking')).toBeVisible({ timeout: 15_000 });
  await panel.locator('#msBooking').selectOption(bookingId);
  await Promise.all([
    waitForUrlBookingId(page, bookingId),
    panel.locator('#milestonePickerForm').getByRole('button', { name: 'Load client' }).click(),
  ]);
  return waitForMainPanel(page);
}

export async function loadPaymentReceiptsForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  const query = new URLSearchParams({ buildingId, bookingId });
  await page.goto(`receipts?${query}`, { waitUntil: 'commit' });
  const panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: 'Payment Receipts' })).toBeVisible();
  await expect(panel.getByRole('button', { name: 'New Payment receipt' })).toBeVisible({
    timeout: 30_000,
  });
  return panel;
}

export async function loadPaymentScheduleForBooking(
  page: Page,
  buildingId: string,
  bookingId: string,
): Promise<Locator> {
  const query = new URLSearchParams({ buildingId, bookingId });
  await page.goto(`bookings/payment-schedule?${query}`, { waitUntil: 'commit' });
  const panel = await waitForMainPanel(page);
  await expect(panel.getByRole('heading', { name: /Payment schedule/i })).toBeVisible();
  await expect(panel.getByText('Slab payment schedule')).toBeVisible({ timeout: 30_000 });
  return panel;
}
