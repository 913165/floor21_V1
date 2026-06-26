import * as readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import type { Page } from '@playwright/test';
import { BUILDER_ADMIN } from './auth';
import { readFlowStateFile } from './flow-state-file';
import { E2E_USER_PASSWORD } from './users';

export type LoginCredentials = {
  label: string;
  email: string;
  password: string;
};

type BrowserPickResult =
  | { kind: 'preset'; index: number }
  | { kind: 'custom'; email: string; password: string };

export function buildLoginOptions(): LoginCredentials[] {
  const options: LoginCredentials[] = [];
  const flow = readFlowStateFile();

  if (flow?.user1?.email) {
    options.push({
      label: `E2E partner — ${flow.user1.fullName} (${flow.user1.email})`,
      email: flow.user1.email,
      password: flow.user1.password || E2E_USER_PASSWORD,
    });
  }
  if (flow?.user2?.email) {
    options.push({
      label: `E2E owner — ${flow.user2.fullName} (${flow.user2.email})`,
      email: flow.user2.email,
      password: flow.user2.password || E2E_USER_PASSWORD,
    });
  }

  options.push({
    label: `Builder admin — ${BUILDER_ADMIN.email}`,
    email: BUILDER_ADMIN.email,
    password: BUILDER_ADMIN.password,
  });

  options.push({
    label: 'Enter email and password manually',
    email: '',
    password: '',
  });

  return options;
}

function parseChoice(raw: string, max: number): number {
  const idx = parseInt(raw.trim(), 10) - 1;
  if (!Number.isFinite(idx) || idx < 0 || idx >= max) {
    throw new Error(`Invalid choice: ${raw}`);
  }
  return idx;
}

function pickFromEnvChoice(options: LoginCredentials[]): LoginCredentials | null {
  const raw = process.env.FLOOR21_LOGIN_CHOICE?.trim();
  if (!raw) {
    return null;
  }
  const picked = options[parseChoice(raw, options.length)]!;
  if (!picked.email) {
    throw new Error(
      'FLOOR21_LOGIN_CHOICE cannot select manual login — set FLOOR21_LOGIN_EMAIL and FLOOR21_LOGIN_PASSWORD instead.',
    );
  }
  return picked;
}

async function promptLoginInTerminal(options: LoginCredentials[]): Promise<LoginCredentials> {
  console.log('\n=== Floor21 — create clients ===\n');
  console.log('Which user should create the clients?\n');
  options.forEach((option, index) => {
    console.log(`  ${index + 1}) ${option.label}`);
  });
  console.log('');

  const rl = readline.createInterface({ input, output });
  try {
    const raw = await rl.question(`Choice [1-${options.length}]: `);
    const picked = options[parseChoice(raw, options.length)]!;

    if (!picked.email) {
      const email = (await rl.question('Email: ')).trim();
      const password = await rl.question('Password: ');
      if (!email || !password) {
        throw new Error('Email and password are required.');
      }
      return { label: email, email, password };
    }

    return picked;
  } finally {
    rl.close();
  }
}

async function promptLoginInBrowser(page: Page, options: LoginCredentials[]): Promise<LoginCredentials> {
  const presetLabels = options
    .map((option, index) => ({ index, label: option.label, manual: !option.email }))
    .filter((option) => !option.manual);

  await page.goto('about:blank');
  await page.setContent(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <title>Floor21 — choose login</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center;
      background: #0f172a; color: #e2e8f0; }
    .card { width: min(520px, 92vw); background: #1e293b; border: 1px solid #334155; border-radius: 12px;
      padding: 1.5rem; box-shadow: 0 12px 40px rgba(0,0,0,.35); }
    h1 { font-size: 1.15rem; margin: 0 0 .35rem; }
    p { margin: 0 0 1rem; color: #94a3b8; font-size: .92rem; }
    button { display: block; width: 100%; text-align: left; margin: .5rem 0; padding: .75rem .9rem;
      border: 1px solid #475569; border-radius: 8px; background: #0f172a; color: inherit; cursor: pointer;
      font-size: .95rem; }
    button:hover { border-color: #60a5fa; background: #172554; }
    form { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid #334155; display: grid; gap: .65rem; }
    label { font-size: .85rem; color: #cbd5e1; }
    input { padding: .55rem .65rem; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: inherit; }
    .submit { text-align: center; background: #2563eb; border-color: #2563eb; }
    .submit:hover { background: #1d4ed8; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Create clients — choose login</h1>
    <p>Select which user should create the clients. Playwright UI mode uses this screen instead of terminal prompts.</p>
    <div id="options"></div>
    <form id="custom-form">
      <label>Email <input id="custom-email" type="email" autocomplete="username" required/></label>
      <label>Password <input id="custom-password" type="password" autocomplete="current-password" required/></label>
      <button class="submit" type="submit">Sign in with custom account</button>
    </form>
  </div>
  <script>
    const presets = ${JSON.stringify(presetLabels)};
    const root = document.getElementById('options');
    presets.forEach((item) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = item.label;
      btn.addEventListener('click', () => {
        window.__e2eLoginPick = { kind: 'preset', index: item.index };
      });
      root.appendChild(btn);
    });
    document.getElementById('custom-form').addEventListener('submit', (event) => {
      event.preventDefault();
      const email = document.getElementById('custom-email').value.trim();
      const password = document.getElementById('custom-password').value;
      if (!email || !password) return;
      window.__e2eLoginPick = { kind: 'custom', email, password };
    });
  </script>
</body>
</html>`);

  const pick = (await page.waitForFunction(() => (window as unknown as { __e2eLoginPick?: BrowserPickResult }).__e2eLoginPick, {
    timeout: 300_000,
  }).then((handle) => handle.jsonValue())) as BrowserPickResult;

  if (pick.kind === 'custom') {
    return { label: pick.email, email: pick.email, password: pick.password };
  }

  const picked = options[pick.index];
  if (!picked?.email) {
    throw new Error('Invalid login selection from browser picker.');
  }
  return picked;
}

export async function resolveLoginCredentials(page: Page): Promise<LoginCredentials> {
  const envEmail = process.env.FLOOR21_LOGIN_EMAIL?.trim();
  const envPassword = process.env.FLOOR21_LOGIN_PASSWORD;
  if (envEmail && envPassword) {
    return { label: envEmail, email: envEmail, password: envPassword };
  }

  const options = buildLoginOptions();
  const fromChoice = pickFromEnvChoice(options);
  if (fromChoice) {
    return fromChoice;
  }

  if (process.stdin.isTTY) {
    return promptLoginInTerminal(options);
  }

  return promptLoginInBrowser(page, options);
}

export function resolveClientCount(): number {
  const raw = process.env.FLOOR21_CLIENT_COUNT?.trim();
  if (!raw) {
    return 5;
  }
  const count = parseInt(raw, 10);
  if (!Number.isFinite(count) || count < 1 || count > 50) {
    throw new Error('FLOOR21_CLIENT_COUNT must be a number between 1 and 50.');
  }
  return count;
}

export async function resolveClientCountInteractive(page: Page): Promise<number> {
  if (process.env.FLOOR21_CLIENT_COUNT) {
    return resolveClientCount();
  }
  if (process.stdin.isTTY) {
    const rl = readline.createInterface({ input, output });
    try {
      const raw = await rl.question('How many clients to create? [5]: ');
      const count = parseInt(raw.trim() || '5', 10);
      if (!Number.isFinite(count) || count < 1 || count > 50) {
        throw new Error('Enter a number between 1 and 50.');
      }
      return count;
    } finally {
      rl.close();
    }
  }

  await page.goto('about:blank');
  await page.setContent(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <title>Floor21 — client count</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center;
      background: #0f172a; color: #e2e8f0; }
    .card { width: min(420px, 92vw); background: #1e293b; border: 1px solid #334155; border-radius: 12px;
      padding: 1.5rem; }
    h1 { font-size: 1.1rem; margin: 0 0 .75rem; }
    input, button { width: 100%; box-sizing: border-box; padding: .65rem; border-radius: 8px;
      border: 1px solid #475569; background: #0f172a; color: inherit; font-size: 1rem; }
    button { margin-top: .75rem; background: #2563eb; border-color: #2563eb; cursor: pointer; }
  </style>
</head>
<body>
  <div class="card">
    <h1>How many clients to create?</h1>
    <form id="count-form">
      <input id="count" type="number" min="1" max="50" value="5" required/>
      <button type="submit">Continue</button>
    </form>
  </div>
  <script>
    document.getElementById('count-form').addEventListener('submit', (event) => {
      event.preventDefault();
      const value = parseInt(document.getElementById('count').value, 10);
      if (!Number.isFinite(value) || value < 1 || value > 50) return;
      window.__e2eClientCount = value;
    });
  </script>
</body>
</html>`);

  const count = await page
    .waitForFunction(() => (window as unknown as { __e2eClientCount?: number }).__e2eClientCount, {
      timeout: 300_000,
    })
    .then((handle) => handle.jsonValue() as Promise<number>);

  if (!Number.isFinite(count) || count < 1 || count > 50) {
    throw new Error('Enter a number between 1 and 50.');
  }
  return count;
}
