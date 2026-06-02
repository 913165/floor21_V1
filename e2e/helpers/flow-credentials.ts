import fs from 'fs';
import path from 'path';
import type { TestInfo } from '@playwright/test';
import { SUPER_ADMIN } from './auth';
import type { PlatformFlowState } from './platform-flow';

const CREDENTIALS_PATH = path.join(__dirname, '..', '.flow-credentials.txt');

export function formatFlowCredentials(flow: PlatformFlowState): string {
  const lines = [
    '=== Floor21 E2E credentials ===',
    '',
    'Platform admin (Admin steps 1–5):',
    `  Email:    ${SUPER_ADMIN.email}`,
    `  Password: ${SUPER_ADMIN.password}`,
    '',
    'Partner users created (Admin step 2):',
    `  User 1 — ${flow.user1.fullName} (Partner steps log in as this user):`,
    `    Email:    ${flow.user1.email}`,
    `    Password: ${flow.user1.password}`,
    `  User 2 — ${flow.user2.fullName}:`,
    `    Email:    ${flow.user2.email}`,
    `    Password: ${flow.user2.password}`,
    '',
    'Partner login (Partner steps 1–5):',
    `  Email:    ${flow.user1.email}`,
    `  Password: ${flow.user1.password}`,
    '',
    'Flow context:',
    `  Project:     ${flow.projectName || '(not created yet)'}`,
    `  Project ID:  ${flow.projectId || '(not created yet)'}`,
    `  Building:    ${flow.building.name || '(not created yet)'}`,
    `  Building ID: ${flow.buildingId || '(not created yet)'}`,
  ];

  if (flow.assignToUser1.length > 0) {
    lines.push(`  Flats for user 1: ${flow.assignToUser1.join(', ')}`);
  }
  if (flow.bookingCode) {
    lines.push(`  Booking code: ${flow.bookingCode}`);
  }

  return lines.join('\n');
}

export function writeFlowCredentialsFile(flow: PlatformFlowState): string {
  const text = formatFlowCredentials(flow);
  fs.writeFileSync(CREDENTIALS_PATH, text + '\n');
  return text;
}

/** Visible in terminal, Playwright UI Log tab, and test attachments. */
export function emitFlowCredentials(flow: PlatformFlowState, testInfo?: TestInfo, label = 'credentials'): void {
  const text = writeFlowCredentialsFile(flow);
  console.log(`\n${text}\n`);
  testInfo?.attach(label, { body: text, contentType: 'text/plain' });
}

export function credentialsFilePath(): string {
  return CREDENTIALS_PATH;
}
