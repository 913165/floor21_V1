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
    'Partner logins (Partner steps — both users book flats):',
    `  Partner 1: ${flow.user1.email} / ${flow.user1.password}`,
    `  Partner 2: ${flow.user2.email} / ${flow.user2.password}`,
    '',
    'Flow context:',
    `  Project:     ${flow.projectName || '(not created yet)'}`,
    `  Project ID:  ${flow.projectId || '(not created yet)'}`,
    `  Building:    ${flow.building.name || '(not created yet)'}`,
    `  Building ID: ${flow.buildingId || '(not created yet)'}`,
  ];

  if (flow.building.parkingFloors != null && flow.building.parkingFloors > 0) {
    lines.push(
      `  Layout:      ${flow.building.totalFloors ?? '?'} floors, ${flow.building.parkingFloors} parking floor(s), ${flow.building.flatsPerFloor ?? '?'} units/floor`,
    );
    if (flow.parkingFlatCount > 0) {
      lines.push(`  Parking slots: ${flow.parkingFlatCount}`);
    }
  }

  if (flow.residentialFlatCount > 0) {
    const assigned = flow.assignToUser1.length + flow.assignToUser2.length;
    lines.push(
      `  Flats assigned: ${assigned} / ${flow.residentialFlatCount} (~90% target)`,
    );
    lines.push(`  User 1 flats (${flow.assignToUser1.length}): ${flow.assignToUser1.join(', ')}`);
    lines.push(`  User 2 flats (${flow.assignToUser2.length}): ${flow.assignToUser2.join(', ')}`);
  }
  if (flow.clients.length > 0) {
    lines.push(`  Clients created: ${flow.clients.length}`);
  }
  if (flow.bookings.length > 0) {
    const byPartner = (email: string) => flow.bookings.filter((b) => b.partnerEmail === email).length;
    lines.push(`  Bookings created: ${flow.bookings.length} total`);
    lines.push(`    Partner 1: ${byPartner(flow.user1.email)}`);
    lines.push(`    Partner 2: ${byPartner(flow.user2.email)}`);
    lines.push(`  Latest booking: ${flow.bookingCode}`);
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
