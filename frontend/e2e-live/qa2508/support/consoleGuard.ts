import { appendFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 2026-08-25 exhaustive round. Unlike qa2408/support/qaListeners.ts (which
// only *records* anomalies into REPORT_LOG.jsonl for later reading), this
// guard *fails the test* the moment an unallowlisted event appears after a
// checkpoint — the user's requirement is "console dev'essere pulita e
// corretta" verified per click, not audited after the fact.

const ARTIFACT_DIR = path.resolve(__dirname, '..', '..', '..', '..', 'qa-artifacts', '2026-08-25');
const EVENTS_FILE = path.join(ARTIFACT_DIR, 'EVENTS.jsonl');
mkdirSync(ARTIFACT_DIR, { recursive: true });

function write(entry: Record<string, unknown>): void {
  appendFileSync(EVENTS_FILE, JSON.stringify({ ts: new Date().toISOString(), ...entry }) + '\n', 'utf-8');
}

export interface AllowRule {
  pattern: RegExp;
  reason: string;
  /** Restrict the allowance to a specific kind; omit to allow across all kinds. */
  scope?: 'console' | 'pageerror' | 'requestfailed' | 'http_error';
}

// Baseline allowlist carried over from the 2026-08-24 pass (qa-artifacts/REPORT.md
// §1) plus this round's own known-legitimate noise. Every entry needs a reason —
// this is a documented exception list, not a generic filter.
export const BASELINE_ALLOWLIST: AllowRule[] = [
  { pattern: /net::ERR_ABORTED/, reason: 'deliberate reload/back/navigation-interrupt test, not a real failure', scope: 'requestfailed' },
  { pattern: /admin_panel_settings/, reason: 'Material Symbols ligature text painted before the font swaps in — cosmetic only', scope: 'console' },
  { pattern: /\[vite\]|\[HMR\]/i, reason: 'Vite HMR socket — irrelevant against the nginx build under test', scope: 'console' },
];

interface CapturedEvent {
  kind: 'console' | 'pageerror' | 'requestfailed' | 'http_error' | 'dialog';
  text: string;
  meta: Record<string, unknown>;
}

export class ConsoleGuard {
  private events: CapturedEvent[] = [];
  private allowlist: AllowRule[];
  private route = '(unset)';
  private role: string;
  private locale: 'it' | 'en';
  private expectingDialog = false;

  constructor(page: Page, opts: { role: string; locale?: 'it' | 'en'; extraAllow?: AllowRule[] }) {
    this.role = opts.role;
    this.locale = opts.locale ?? 'en';
    this.allowlist = [...BASELINE_ALLOWLIST, ...(opts.extraAllow ?? [])];

    page.on('console', (msg) => {
      const type = msg.type();
      if (type !== 'error' && type !== 'warning') return;
      this.push('console', msg.text(), { level: type, location: msg.location() });
    });
    page.on('pageerror', (err) => this.push('pageerror', err.message, { stack: err.stack }));
    page.on('requestfailed', (req) =>
      this.push('requestfailed', req.failure()?.errorText ?? 'unknown', { url: req.url(), method: req.method() }));
    page.on('response', (res) => {
      if (res.status() < 400) return;
      this.push('http_error', `${res.status()} ${res.url()}`, { url: res.url(), status: res.status(), method: res.request().method() });
    });
    page.on('crash', () => this.push('pageerror', 'PAGE CRASHED', {}));
    page.on('dialog', (dialog) => {
      write({ kind: 'dialog', message: dialog.message(), route: this.route, role: this.role, expected: this.expectingDialog });
      if (!this.expectingDialog) {
        // An unexpected native dialog blocks all further automation — surface it
        // immediately as a captured event rather than letting the run hang.
        this.push('dialog', `unexpected window.${dialog.type()}(): ${dialog.message()}`, {});
      }
    });
  }

  private push(kind: CapturedEvent['kind'], text: string, meta: Record<string, unknown>): void {
    const allow = this.allowlist.find((r) => (!r.scope || r.scope === kind) && r.pattern.test(text));
    write({ kind, text, route: this.route, role: this.role, locale: this.locale, allowlisted: allow?.reason ?? null, ...meta });
    if (allow) return;
    this.events.push({ kind, text, meta });
  }

  setRoute(route: string): void {
    this.route = route;
  }

  /** Register that the next click is expected to open a native dialog (handled by the caller). */
  expectDialog<T>(fn: () => Promise<T>): Promise<T> {
    this.expectingDialog = true;
    return fn().finally(() => {
      this.expectingDialog = false;
    });
  }

  /**
   * Call after every click/interaction. Throws with full context if anything
   * un-allowlisted arrived since the previous checkpoint — this is what makes
   * "console pulita ad ogni click" an enforced assertion, not a hope.
   */
  checkpoint(label: string): void {
    if (this.events.length === 0) return;
    const dump = this.events
      .map((e) => `  [${e.kind}] ${e.text} ${JSON.stringify(e.meta)}`)
      .join('\n');
    const failed = this.events;
    this.events = [];
    throw new Error(
      `ConsoleGuard checkpoint "${label}" failed on route=${this.route} role=${this.role} locale=${this.locale}\n`
        + `${failed.length} unallowlisted event(s):\n${dump}`,
    );
  }

  /** Non-throwing peek, for chaosWalker which needs to record-and-continue rather than abort the whole session. */
  drain(): CapturedEvent[] {
    const events = this.events;
    this.events = [];
    return events;
  }
}

/** The one deliberate console.error the app ever emits on purpose — always a broken screen. */
export const ERROR_BOUNDARY_PATTERN = /\[ErrorBoundary\] Unhandled render error/;
