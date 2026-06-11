import fs from 'fs';
import path from 'path';
import type { TestInfo } from '@playwright/test';
import { SUPER_ADMIN } from './auth';
import { E2E_PRIMARY_BUYER } from './clients';
import type { PlatformFlowState } from './platform-flow';
import { E2E_USER_PASSWORD } from './users';

const CREDENTIALS_PATH = path.join(__dirname, '..', '.flow-credentials.txt');

export function formatFlowCredentials(flow: PlatformFlowState): string {
  const lines = [
    '=== Floor21 E2E credentials ===',
    '',
    'Platform admin (Admin steps 1–5):',
    `  Email:    ${SUPER_ADMIN.email}`,
    `  Password: ${SUPER_ADMIN.password}`,
    '',
    `Partner users created (Admin step 2) — password for all: ${E2E_USER_PASSWORD}`,
    `  User 1 — ${flow.user1.fullName} (Partner; partner steps log in as this user):`,
    `    Email:    ${flow.user1.email}`,
    `  User 2 — ${flow.user2.fullName} (Owner):`,
    `    Email:    ${flow.user2.email}`,
    '',
    'Partner logins (Partner steps — partner + owner both book flats):',
    `  Partner: ${flow.user1.email} / ${E2E_USER_PASSWORD}`,
    `  Owner:   ${flow.user2.email} / ${E2E_USER_PASSWORD}`,
    '',
    'Primary demo buyer (Partner 1 — first client; CRM record, no login):',
    `  Name:  ${E2E_PRIMARY_BUYER.firstName} ${E2E_PRIMARY_BUYER.lastName}`,
    `  Email: ${E2E_PRIMARY_BUYER.email}`,
    '',
    'Flow context:',
    `  Project:     ${flow.projectName || '(not created yet)'}`,
    `  Project ID:  ${flow.projectId || '(not created yet)'}`,
    `  Building:    ${flow.building.name || '(not created yet)'}`,
    `  Building ID: ${flow.buildingId || '(not created yet)'}`,
  ];

  if (flow.building.parkingFloors != null && flow.building.parkingFloors > 0) {
    lines.push(
      `  Layout:      ${flow.building.totalFloors ?? '?'} floors (${flow.building.parkingFloors ?? 0} parking + ${Math.max(0, (flow.building.totalFloors ?? 0) - (flow.building.parkingFloors ?? 0))} residential), ${flow.building.flatsPerFloor ?? '?'} units/floor`,
    );
    if (flow.building.bhkPerFloor && Object.keys(flow.building.bhkPerFloor).length > 0) {
      const mix = Object.entries(flow.building.bhkPerFloor)
        .filter(([, count]) => (count ?? 0) > 0)
        .map(([type, count]) => `${type}×${count}`)
        .join(', ');
      lines.push(`  Unit mix:    ${mix} per residential floor`);
    } else if (flow.building.twoBhkPerFloor != null) {
      lines.push(`  Unit mix:    2BHK×${flow.building.twoBhkPerFloor} per residential floor`);
    }
    if (flow.parkingFlatCount > 0) {
      lines.push(`  Parking slots: ${flow.parkingFlatCount}`);
      const perFloor =
        flow.building.parkingFloors && flow.building.parkingFloors > 0
          ? Math.round(flow.parkingFlatCount / flow.building.parkingFloors)
          : 0;
      if (perFloor > 0) {
        lines.push(`  Parking slots/floor: ${perFloor} (default; configurable in UI)`);
      }
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
    const primary = flow.clients.find((c) => c.email === E2E_PRIMARY_BUYER.email);
    if (primary) {
      lines.push(`  Primary buyer booking client: ${primary.displayName} (${primary.email})`);
    }
  }
  if (flow.bookings.length > 0) {
    const byPartner = (email: string) => flow.bookings.filter((b) => b.partnerEmail === email).length;
    lines.push(`  Bookings created: ${flow.bookings.length} total`);
    lines.push(`    Partner 1: ${byPartner(flow.user1.email)}`);
    lines.push(`    Partner 2: ${byPartner(flow.user2.email)}`);
    lines.push(`  Latest booking: ${flow.bookingCode}`);
  }
  if (flow.unitTypeDefaults2Bhk) {
    const d = flow.unitTypeDefaults2Bhk;
    lines.push(
      `  2BHK defaults: ${d.bhkType} · ${d.areaSqft} sq ft · carpet ${d.carpetAreaSqft} · price ${d.basePrice}`,
    );
  }

  if (flow.parkingLinks.length > 0) {
    lines.push(`  Parking links: ${flow.parkingLinks.length} (random sample of booked flats)`);
  }
  if (flow.receipts?.length > 0) {
    lines.push(`  Payment receipts: ${flow.receipts.length}`);
    for (const r of flow.receipts) {
      lines.push(`    ${r.clientDisplayName}: ₹${r.amount.toLocaleString('en-IN')} (${r.chequeNo})`);
    }
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
