import { expect, type Locator, type Page } from '@playwright/test';
import { mainPanel } from './auth';
import { openNewProjectForm as openNewProjectFormViaNav, openProjectsList as openProjectsListViaNav } from './nav';

export function uniqueProjectName(label = 'E2E Project'): string {
  return `${label} ${Date.now()}`;
}

export async function waitForMainPanel(page: Page): Promise<Locator> {
  const main = mainPanel(page);
  await main.waitFor({ state: 'attached', timeout: 15_000 });
  await page.waitForFunction(
    () => {
      const frame = document.getElementById('floor21-main');
      return frame != null && frame.getAttribute('aria-busy') !== 'true';
    },
    { timeout: 30_000 },
  );
  return main;
}

export async function openProjectsList(page: Page): Promise<Locator> {
  return openProjectsListViaNav(page);
}

export async function openNewProjectForm(page: Page): Promise<Locator> {
  return openNewProjectFormViaNav(page);
}

export async function fillNewProjectForm(
  main: Locator,
  data: { name: string; city?: string; address?: string; active?: boolean },
) {
  await main.locator('#companyName').fill(data.name);
  if (data.city != null) {
    await main.locator('#city').fill(data.city);
  }
  if (data.address != null) {
    await main.locator('#address').fill(data.address);
  }
  const active = main.locator('input[type=checkbox][name="active"]');
  if (data.active === false) {
    await active.uncheck();
  } else if (data.active === true) {
    await active.check();
  }
}

export async function submitProjectForm(main: Locator) {
  await main.getByRole('button', { name: 'Save' }).click();
}

export async function createProject(
  page: Page,
  data: { name: string; city?: string; address?: string; active?: boolean },
): Promise<{ projectId: string; list: Locator }> {
  const main = await openNewProjectForm(page);
  await fillNewProjectForm(main, data);
  await submitProjectForm(main);

  const list = await waitForMainPanel(page);
  await expect(list.getByText('Project saved')).toBeVisible({ timeout: 30_000 });
  await expect(list.getByRole('heading', { name: 'Projects' })).toBeVisible();

  const row = list.locator('tbody tr').filter({ hasText: data.name }).first();
  await expect(row).toBeVisible();
  const projectId = projectIdFromPartnersLink(
    await row.getByRole('link', { name: /owner\/partner/i }).getAttribute('href'),
  );
  return { projectId, list };
}

export function projectIdFromPartnersLink(href: string | null): string {
  const match = href?.match(/\/admin\/projects\/([0-9a-f-]+)\/staff/i);
  if (!match) {
    throw new Error(`Could not parse project id from href: ${href}`);
  }
  return match[1];
}

export function buildingIdFromFlatsUrl(url: string): string {
  const match = url.match(/\/buildings\/([0-9a-f-]+)\/flats/i);
  if (!match) {
    throw new Error(`Could not parse building id from url: ${url}`);
  }
  return match[1];
}
