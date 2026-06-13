import { expect, type Locator, type Page } from '@playwright/test';

async function expectNoModalBackdrop(page: Page): Promise<void> {
  await expect(page.locator('.modal-backdrop')).toHaveCount(0, { timeout: 10_000 });
}

async function expectModalDismissed(page: Page, modal: Locator): Promise<void> {
  await expect(modal).not.toHaveClass(/show/, { timeout: 10_000 });
  await expect(modal).toBeHidden({ timeout: 10_000 });
  await expectNoModalBackdrop(page);
}

/** Prefer footer Close/Cancel; fall back to header X when no footer dismiss control exists. */
async function clickModalDismissButton(modal: Locator): Promise<void> {
  const footerDismiss = modal
    .locator('.modal-footer')
    .getByRole('button', { name: /^Close$|^Cancel$/ });
  if ((await footerDismiss.count()) > 0) {
    const btn = footerDismiss.first();
    await btn.scrollIntoViewIfNeeded();
    await btn.click();
    return;
  }
  const headerClose = modal.locator('.modal-header .btn-close');
  await expect(headerClose).toBeVisible({ timeout: 10_000 });
  await headerClose.scrollIntoViewIfNeeded();
  await headerClose.click();
}

/** Move modal out of turbo-frame so Bootstrap backdrop and show() work (same as flat-grid.js). */
export async function mountTurboFrameModalOnBody(page: Page, modalId: string): Promise<void> {
  await page.evaluate((id) => {
    const matches = document.querySelectorAll(`[id="${id}"]`);
    if (!matches.length) return;
    const main = document.getElementById('floor21-main');
    let keep = matches[matches.length - 1] as HTMLElement;
    if (main && matches.length > 1) {
      matches.forEach((node) => {
        if (main.contains(node)) keep = node as HTMLElement;
      });
    }
    matches.forEach((node) => {
      if (node !== keep) node.remove();
    });
    if (keep.parentElement !== document.body) {
      document.body.appendChild(keep);
    }
  }, modalId);
}

/** Open a Bootstrap modal by id (turbo-frame safe). */
export async function openBootstrapModal(page: Page, modalId: string): Promise<Locator> {
  await mountTurboFrameModalOnBody(page, modalId);
  const modal = page.locator(`#${modalId}`);
  await page.evaluate((id) => {
    const modalEl = document.getElementById(id);
    if (!modalEl) throw new Error(`Modal #${id} not found`);
    const bootstrap = (
      window as unknown as {
        bootstrap?: { Modal?: { getOrCreateInstance: (el: Element) => { show: () => void } } };
      }
    ).bootstrap;
    if (!bootstrap?.Modal) throw new Error('Bootstrap Modal not available');
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }, modalId);
  await expect(modal).toHaveClass(/show/, { timeout: 10_000 });
  return modal;
}

export async function closeReceiptFormModalIfOpen(page: Page): Promise<void> {
  const modal = page.locator('#receipt-form-modal.modal.show').last();
  if ((await modal.count()) === 0) {
    return;
  }
  const cancel = modal.getByRole('button', { name: 'Cancel' });
  if (await cancel.isVisible()) {
    await cancel.click();
  } else {
    await modal.locator('.modal-header .btn-close').click();
  }
  await expectModalDismissed(page, modal);
}

/** Flat details popup — prefer inline Close next to Save; fall back to header X. */
export async function closeFlatDetailsModal(page: Page, modal?: Locator): Promise<void> {
  const target = modal ?? page.locator('#flat-details-modal');
  if (!(await target.isVisible())) {
    return;
  }
  const inlineClose = target.locator('#admin-close-btn');
  if (await inlineClose.isVisible()) {
    await inlineClose.scrollIntoViewIfNeeded();
    await inlineClose.click();
  } else {
    await target.locator('.modal-header .btn-close').click();
  }
  await expectModalDismissed(page, target);
}

/** Dismiss an open Bootstrap modal by id (ground floor, parking config, clients import, etc.). */
export async function dismissBootstrapModal(page: Page, modalId: string): Promise<void> {
  const modal = page.locator(`#${modalId}.modal.show`).last();
  if ((await modal.count()) === 0) {
    return;
  }
  await clickModalDismissButton(modal);
  await expect(page.locator(`#${modalId}`).first()).not.toHaveClass(/show/, { timeout: 10_000 });
  await expectNoModalBackdrop(page);
}

/** Clients import modal — footer Close (Turbo may leave duplicate markup; target open instance). */
export async function closeClientsImportModalIfOpen(page: Page): Promise<void> {
  const modal = page.locator('#clients-import-modal.modal.show').last();
  if ((await modal.count()) === 0) {
    return;
  }
  await clickModalDismissButton(modal);
  await expect(modal).not.toHaveClass(/show/, { timeout: 10_000 });
  await expectNoModalBackdrop(page);
}
