import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const INVENTORY_DIR = path.resolve(__dirname, '..', '..', '..', '..', 'qa-artifacts', '2026-08-25', 'inventory');
mkdirSync(INVENTORY_DIR, { recursive: true });

export type ElementClass = 'safe' | 'modal' | 'destructive' | 'navigational' | 'download' | 'deferred';

export interface InventoryEntry {
  role: string;
  name: string;
  testId: string | null;
  domId: string | null;
  ariaPressed: string | null;
  classification: ElementClass;
  exercised: boolean;
  exclusionReason: string | null;
}

const INTERACTIVE_ROLES = [
  'button', 'link', 'checkbox', 'switch', 'radio', 'tab', 'combobox',
  'textbox', 'spinbutton', 'menuitem', 'option',
] as const;

/**
 * Snapshots every interactive node on the current page via the accessibility
 * tree, not a raw DOM query — this is what lets the sweep match how
 * getByRole()-based specs (frontend/e2e/*.spec.ts) already address elements,
 * and is immune to class-name churn from styling changes.
 */
/** Minimal shape of the nodes page.accessibility.snapshot() returns — only the fields this sweep reads. */
interface AccessibilityNode {
  role: string;
  name?: string;
  pressed?: boolean | 'mixed';
  children?: AccessibilityNode[];
}

export async function extractInventory(page: Page): Promise<InventoryEntry[]> {
  const snapshot = await page.accessibility.snapshot({ interestingOnly: true });
  const out: InventoryEntry[] = [];

  function walk(node: AccessibilityNode | null): void {
    if (!node) return;
    if ((INTERACTIVE_ROLES as readonly string[]).includes(node.role)) {
      out.push({
        role: node.role,
        name: node.name ?? '',
        testId: null, // enriched separately via DOM query below when needed
        domId: null,
        ariaPressed: node.pressed !== undefined ? String(node.pressed) : null,
        classification: 'safe',
        exercised: false,
        exclusionReason: null,
      });
    }
    for (const child of node.children ?? []) walk(child);
  }
  walk(snapshot);
  return out;
}

/** Applies the deliberate classification rules from the plan (Blocco 3) to a raw inventory. */
export function classify(entries: InventoryEntry[]): InventoryEntry[] {
  const DESTRUCTIVE_NAME = /delete|elimina|remove|rimuovi|deactivate|disattiva|decline|rifiuta/i;
  const NAV_NAME = /^view$|visualizza|^edit$|modifica|back|indietro/i;
  const DOWNLOAD_NAME = /download|scarica|export|esporta/i;
  const DEFERRED_NAME = /log ?out|logout|esci/i;
  const MODAL_NAME = /^add|^new|^aggiungi|^nuovo|edit|modifica|settings|impostazioni/i;

  return entries.map((e) => {
    if (DEFERRED_NAME.test(e.name)) return { ...e, classification: 'deferred' as const };
    if (DESTRUCTIVE_NAME.test(e.name)) return { ...e, classification: 'destructive' as const };
    if (DOWNLOAD_NAME.test(e.name)) return { ...e, classification: 'download' as const };
    if (NAV_NAME.test(e.name)) return { ...e, classification: 'navigational' as const };
    if (MODAL_NAME.test(e.name) && e.role === 'button') return { ...e, classification: 'modal' as const };
    return { ...e, classification: 'safe' as const };
  });
}

export function saveInventory(route: string, roleLabel: string, entries: InventoryEntry[]): void {
  const fileName = `${route.replace(/[^a-z0-9_-]/gi, '_') || 'root'}.${roleLabel}.json`;
  writeFileSync(path.join(INVENTORY_DIR, fileName), JSON.stringify(entries, null, 2), 'utf-8');
}

/**
 * Coverage gate: fails if any inventory entry on disk was never marked
 * exercised and has no exclusion reason. Run once at the end of Blocco 3.
 */
export function coverageCheck(entries: InventoryEntry[], route: string, roleLabel: string): void {
  const untouched = entries.filter((e) => !e.exercised && !e.exclusionReason);
  if (untouched.length > 0) {
    throw new Error(
      `coverage-check failed for ${route} (${roleLabel}): ${untouched.length} element(s) never exercised or excluded:\n`
        + untouched.map((e) => `  [${e.role}] "${e.name}"`).join('\n'),
    );
  }
}
