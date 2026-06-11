import fs from 'fs';
import path from 'path';
import type { PlatformFlowState } from './platform-flow';

const STATE_PATH = path.join(__dirname, '..', '.flow-state.json');

export function readFlowStateFile(): PlatformFlowState | null {
  try {
    if (!fs.existsSync(STATE_PATH)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(STATE_PATH, 'utf-8')) as PlatformFlowState;
  } catch {
    return null;
  }
}

export function writeFlowStateFile(flow: PlatformFlowState): void {
  fs.writeFileSync(STATE_PATH, JSON.stringify(flow, null, 2));
}

export function requireFlowState(flow: PlatformFlowState): void {
  const saved = readFlowStateFile();
  if (!saved?.projectId || !saved.user1?.email || !saved.buildingId) {
    throw new Error(
      'Flow state missing. Run Admin steps 1–5b first (or run the full suite from the top).',
    );
  }
  Object.assign(flow, saved);
  if (!flow.clientDisplayName) {
    flow.clientDisplayName = `${flow.clientFirstName} ${flow.clientLastName}`.trim();
  }
}

export function requireBuildingFlow(flow: PlatformFlowState): void {
  if (!flow.projectId || !flow.buildingId) {
    throw new Error(
      'Building not in flow state. Run Admin — 3. Create building first, or restart the suite from Admin — 1.',
    );
  }
}
