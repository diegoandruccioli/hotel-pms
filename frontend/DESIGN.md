# DESIGN.md — hotel-pms frontend

## Purpose & how to use this file

This is what an AI coding agent (or a human contributor) should read before writing
or editing any UI code in this repo. It doesn't replace `CLAUDE.md` or
`CONTRIBUTING.md` — those own the project-wide rules (stack, testing thresholds,
security, i18n policy). This file owns the *design-system detail* those rules point
to: the actual token values, component-authoring pattern, and layout discipline that
keep every screen looking like it belongs to the same product, whether a human or an
agent built it.

Write UI code the way the existing `frontend/src/components/m3/*` components are
written, not the way a generic React tutorial would. The rules below explain why,
briefly, then state the rule — read the "why" once, then treat the rule as load-bearing.

---

## Design tokens — colors

**Never hardcode a hex color in a component.** Always use the semantic Tailwind
classes backed by `var(--md-*)` custom properties defined in
`frontend/src/styles/m3-base.css`, wired into `frontend/tailwind.config.js`:

- `primary` / `on-primary` / `primary-container` / `on-primary-container`
- `secondary` / `on-secondary` / `secondary-container` / `on-secondary-container`
- `tertiary` / `on-tertiary` / `tertiary-container` / `on-tertiary-container`
- `error` / `on-error` / `error-container` / `on-error-container`
- `surface` / `surface-variant` / `surface-dim` / `surface-bright` /
  `surface-container-lowest` / `-low` / (DEFAULT) / `-high` / `-highest`
- `on-surface` / `on-surface-variant`
- `outline` / `outline-variant`
- `inverse-surface` / `inverse-on-surface` / `inverse-primary`
- `scrim`

**One dominant accent, used consistently.** `primary` is the product's single accent
— buttons, active nav state, chart emphasis, links. Reserve `secondary`/`tertiary`
for genuine secondary emphasis, not decorative variety. Don't reach for a third or
fourth color just because a screen "feels busy" — a dense PMS dashboard stays
readable by using *one* accent everywhere and reserving color for meaning, not decoration.

**`error` (and success, where a green token is used) is semantic-only.** It means
"this value is bad" or "this value is good" — a negative delta, a failed status chip,
a destructive action. Never use error/success red or green as a decorative accent on
something that isn't actually a state. A dashboard that colors every card a different
hue to look "vibrant" is the anti-pattern here, not the goal.

**Every token has four values, not two.** Light, dark, light-high-contrast, and
dark-high-contrast (`[data-contrast="high"]`). The WCAG AAA high-contrast theme is a
maintained, first-class variant — see Accessibility below — not a legacy or optional
mode. When you add a new semantic token, you add all four values.

---

## Shape, elevation, motion

**Border radius (M3 shape scale)** — `frontend/tailwind.config.js`:

| Class | Value |
|---|---|
| `rounded-shape-xs` | 4px |
| `rounded-shape-sm` | 8px |
| `rounded-shape-md` | 12px |
| `rounded-shape-lg` | 16px |
| `rounded-shape-xl` | 28px |
| `rounded-shape-full` | 9999px |

Pick from this scale — don't write an arbitrary `rounded-[Npx]`. Cards use `shape-md`,
chips/buttons typically `shape-sm`/`shape-full`; match the existing `m3/` components
for the component type you're building.

**Elevation** — `shadow-elevation-0` (none) through `shadow-elevation-5`, values
defined in `m3-base.css` for both light and dark (dark uses higher shadow alpha:
0.30/0.50 vs light's 0.15/0.30, since a dark surface needs stronger shadows to read).
Don't write a bespoke `box-shadow`.

**Glass surfaces** — two purpose-built utility classes exist for translucent
panels: `.glass-surface` (16px blur) and `.glass-surface-elevated` (24px blur),
backed by `--md-glass-bg`/`--md-glass-border` tokens (light/dark variants). Use these
instead of inventing a new `backdrop-blur` + `bg-white/70` combination — they're
already tuned per-theme.

**Motion** — a global rule in `m3-base.css` applies a 150ms
`cubic-bezier(0.2,0,0,1)` transition to `color`/`background-color`/`border-color`/
`box-shadow`/`opacity` on every element. Don't add a per-component transition for
these properties — you'd be fighting or duplicating the global one. Opt out with
`[data-no-transition]` only when the global transition actively breaks something
(e.g. an instant theme-swap moment). `prefers-reduced-motion: reduce` zeroes all
animations/transitions automatically (WCAG 2.3.3) — this is handled globally, don't
re-guard individual animations for it.

---

## Typography

Two font families, both self-hosted via `@fontsource`:

- `font-display` → Outfit — headings, large numeric values (KPI figures, prices),
  anything meant to draw the eye first.
- `font-body` → Inter — body text, labels, table content, form inputs.

**Font size scales with the user's accessibility preference**, not with a fixed
`px` value: `--md-font-scale` (small 14px / normal 16px / large 18px, set by
`settingsStore`) drives `html { font-size: var(--md-font-scale) }`, and the whole UI
scales because Tailwind's type scale is `rem`-based. Use Tailwind's text-size
utilities (`text-sm`, `text-base`, `text-lg`, …) — never a literal `px` font-size —
or you silently break that preference for the user who needs larger text.

**Known gap**: there is no custom Tailwind `fontSize` or `spacing` scale in
`tailwind.config.js` — both are stock Tailwind defaults, extended for nothing else.
This is intentional-by-default rather than a deliberate design decision on record.
Use the stock scale consistently (`p-4`, `gap-6`, `text-sm`, …) rather than
one-off arbitrary values (`p-[13px]`) — consistency with the existing screens matters
more than pixel-perfect matching to an external mock.

---

## Component authoring pattern

Follow the convention already established by `M3Button.tsx`, `M3Card.tsx`,
`M3StatusChip.tsx` and the rest of `frontend/src/components/m3/`:

1. **Named export only** — `export const M3Thing = ({...}: M3ThingProps) => ...`.
   No default export (matches the "PascalCase filename = component name" rule in
   `CLAUDE.md`).
2. **Props interface extends the native element's attributes** —
   `interface M3ThingProps extends React.ButtonHTMLAttributes<HTMLButtonElement>`
   (or the appropriate `HTMLAttributes<...>`) — so consumers can pass any native
   prop through without a wrapper API drifting from the platform.
3. **Spread `...rest` onto the underlying DOM node** last, after your own
   destructured props, so callers can still override/extend.
4. **Variant as a union type + `Record<Variant, string>` lookup table** for the
   class string per variant, e.g. `type ButtonVariant = 'filled'|'tonal'|'outlined'|'text'`.
   Don't branch variant styling with a chain of ternaries inline in JSX.
5. **Use `cn()` from `frontend/src/utils/cn.ts`** (`twMerge(clsx(inputs))`) to
   compose the final class string, so a caller's override always wins over the
   component's default per Tailwind-merge conflict resolution — see `M3Button.tsx`,
   `M3Card.tsx`, `M3StatusChip.tsx` for the pattern (all three were migrated off
   template-literal concatenation on 2026-09-02).
6. **Disabled state**: `opacity-38 cursor-not-allowed shadow-none` (the `opacity-38`
   Tailwind value is the M3 disabled-state token, not an arbitrary number).
7. **Focus-visible ring is mandatory on every interactive element**, not just
   inherited from a browser default: `focus-visible:ring-2 focus-visible:ring-primary
   focus-visible:ring-offset-2` (use `focus-within:ring-2` on the wrapper instead for
   a bordered input/select whose focus state lives on a child element, and
   `focus-visible:ring-inset` for a full-width row inside a menu/listbox where an
   offset ring would clip against the container edge). This is a first-class design
   element per the Accessibility section below, not an afterthought to add if time
   allows.
8. **A file never imports its own folder's barrel (`index.ts`)** — always import
   sibling files directly (`./Foo`, not `./index` or `.`). This is the single most
   common cause of a circular-import bug once a folder has a barrel; `npm run
   lint:cycles` (`madge --circular`, wired into CI) catches any cycle this rule
   doesn't prevent, but don't rely on the guard instead of following the rule.
   **`madge --circular` does not catch every class of import bug** — verified
   the hard way (2026-09-02): a page file and its own subfolder sharing a name
   (`pages/AdminUsers.tsx` + `pages/AdminUsers/`) makes `from './AdminUsers'`
   *inside `AdminUsers.tsx`* resolve back to `AdminUsers.tsx` itself (the exact
   filename wins module resolution over the same-named directory) — a genuine
   self-import. `tsc -b` rejected it immediately (`TS2303 Circular definition of
   import alias`); `madge --circular` reported zero cycles for the same code.
   The lesson isn't "trust madge less" — `npm run build` (which runs `tsc -b`)
   is already a separate, mandatory CI step — it's that the two checks catch
   different things and neither substitutes for the other. Concretely: a
   page whose subfolder shares its own name can never import that subfolder's
   barrel from within the page file itself; import the specific sibling file
   instead (`./AdminUsers/CreateUserModal`, not `./AdminUsers`).
9. **Never import from `components/index.ts`.** Unlike every other barrel in
   this codebase, it is unsafe to consume — `./ErrorBoundary` wraps a class
   component with react-i18next's `withTranslation` HOC at module scope
   (mandatory: error boundaries can't use the `useTranslation` hook), so
   merely importing *anything* from this barrel runs that HOC immediately,
   which broke 38 test files whose `react-i18next` mock only stubbed
   `useTranslation`. This isn't fixable the way the two eager-i18n-load bugs
   below were (deferring behind a dynamic `import()`) — the wrapped component
   has to exist synchronously for JSX to render it. The barrel file is kept
   only for consistency with the folder-barrel rule; every consumer imports
   each component directly from its own file (`./components/Toast`, etc.).
   If a future component in this folder is added *without* a module-scope HOC
   or other import-time side effect, that alone still doesn't make the whole
   barrel safe — anything re-exported alongside `ErrorBoundary` inherits its
   eager evaluation the moment the barrel is touched at all.

---

## Dashboard / layout composition rules

PMS dashboards are inherently data-dense — the discipline that keeps a dense screen
readable is *consistency*, not *sparseness*. Concretely:

- **Consistent card padding and radius across a screen.** Every card in a dashboard
  row uses the same `rounded-shape-md`, the same internal padding — don't vary these
  per-card even if one card "feels like it needs more room."
- **Repeated internal card hierarchy**: small muted label → large bold value → small
  colored delta/trend indicator. Every KPI card on a screen should follow this exact
  visual rhythm so a user can scan the row without re-parsing each card's layout from
  scratch.
- **Generous section spacing even at high density.** Don't compress `gap`/`p-*`
  values to cram more onto a screen — if a screen has too much to show, that's a
  signal to use `M3DataTable` with pagination, or `@tanstack/react-virtual`
  (already a dependency, backs the ">50 items" virtualization rule in `CLAUDE.md`),
  not to shrink whitespace.
- **Light / dark / high-contrast are token swaps, never a parallel redesign.**
  Toggling theme should never change *which* components render or *how* they're
  laid out — only the token values underneath. If a component looks meaningfully
  different in dark mode beyond color, that's a bug, not a feature.

---

## Accessibility

Single canonical statement — this is now the *only* place these rules are stated.
`CLAUDE.md`, `CONTRIBUTING.md`, `backup/DECISIONS.md` §4.2, and
`docs/COMPLIANCE_AUDIT_2026-08.md` §8 have been trimmed to point here instead of
restating the rules (each keeps only content specific to that file — e.g.
COMPLIANCE_AUDIT keeps the PDF/UA fiscal-document note, DECISIONS keeps the
historical baseline pointer).

- Contrast: **7:1 minimum for normal text, 4.5:1 for large text** as the baseline —
  this is already WCAG **AAA**-level (WCAG 1.4.6), not just AA.
- Touch targets: **minimum 40×40px** (MD3 "Large" size — chosen deliberately to
  preserve information density on PMS dashboards rather than the MD3 default).
- **Entire UI navigable using TAB alone.** Every interactive element keyboard
  operable (Tab / Enter / Space / Arrow / Escape).
- **Focus trapped inside open modals/dialogs** (`focus-trap-react`); Escape closes.
- **Skip-to-main-content link** is the first focusable element on every page.
- Semantic HTML first (`<nav>`, `<main>`, `<dialog>`, …) — ARIA only when semantic
  HTML is insufficient.
- Every form input: `<label htmlFor>` or `aria-label`/`aria-labelledby`.
- Every image: `alt` required; decorative images: `alt="" aria-hidden="true"`.
- `vitest-axe` runs on every component test — a new component without an axe
  assertion in its test file is incomplete, not "fine for now."
- Focus ring is a first-class design element (see Component authoring pattern) —
  never removed, never an `outline-hidden` without a visible `ring-*` replacement
  (Tailwind v4 renamed `outline-none` → `outline-hidden`; the old name now means
  "no outline ever, including forced-colors mode" — don't use it by accident).

**Dedicated WCAG AAA high-contrast mode.** Beyond the AAA-level baseline above, the
project ships a *separate, distinct* high-contrast theme —
`[data-contrast="high"]` in `m3-base.css`, toggled by `settingsStore`'s
`ContrastMode` ('normal' | 'high') — for users who need contrast pushed further than
the baseline provides. This is not redundant with the AAA baseline and must not be
folded away or treated as optional:

| Token | Light HC | Dark HC |
|---|---|---|
| `primary` | `#003070` | `#C8DFFF` |
| `surface` | `#FFFFFF` | `#000000` |
| `on-surface` | `#000000` | `#FFFFFF` |
| `outline` | `#000000` | `#FFFFFF` |

These push core text/outline pairs to near-maximum contrast (~21:1). **Rule for
agents: any new semantic color token added to `m3-base.css`'s `:root`/`.dark` must
get a corresponding `[data-contrast="high"]` / `.dark[data-contrast="high"]` value in
the same change.** Don't add a token to the base themes only and let the
high-contrast variant silently fall back to an unstyled or low-contrast default —
that regresses accessibility for users who explicitly opted into this mode.

---

## Icons

The actual icon system is **`material-symbols`**, consumed through the
`MaterialIcon` component (`frontend/src/components/MaterialIcon.tsx`) — props
`name` / `className` / `filled` / `size` / `label`. An icon is decorative
(`aria-hidden`, no `role`) unless you pass `label`, in which case it becomes
`role="img" aria-label={label}`. *(Note: `CLAUDE.md`'s stack line says "Lucide
React" — that's stale; `lucide-react` isn't a dependency. Use `material-symbols` /
`MaterialIcon`, not Lucide.)*

---

## i18n

All UI strings go through `react-i18next`, zero hardcoded text. Key naming
convention (snake_case, functional prefixes `nav_`/`label_`/`err_`/`msg_`/
`action_`/`hint_`/`tab_`/`stat_`/`status_`/`role_`/`payment_method_`, short common
keys unprefixed) is authoritatively documented in `docs/I18N.md` §4 — read that
before adding new keys, don't improvise a naming pattern here.

---

## Known gaps / drift

Kept explicit rather than papered over, so this file stays honest about the current
state instead of describing an aspirational one:

- **No custom Tailwind `fontSize`/`spacing` scale** — stock Tailwind defaults are
  used as-is; there's no documented design decision behind that, it's just how the
  config currently is. Still true as of this update.
- **Resolved (2026-09-02)**: `M3Button`, `M3Card`, and `M3StatusChip` now use `cn()`
  from `utils/cn.ts` instead of template-literal class concatenation, matching the
  intended pattern in Component authoring pattern above.
- **Barrel exports created (2026-09-02), but not wired up as the import path.**
  Every `src/` subfolder with 2+ non-test source files now has an `index.ts`
  re-exporting its members (e.g. `components/index.ts`, `services/index.ts`,
  `pages/Stays/index.ts`, …) — `frontend/src/pages/Rooms/index.tsx` remains the one
  exception, since it's the page component itself, not a re-export barrel.
  **Existing imports throughout the codebase still use deep paths** (e.g.
  `from '../../services/stayService'`) and were deliberately left unchanged — a
  codebase-wide switch to barrel imports was explicitly descoped, since folders
  like `services/`, `store/`, and `hooks/queries` cross-reference each other and
  routing all of that through barrels is a well-known way to introduce circular
  ESM imports (`Cannot access 'X' before initialization` at runtime) in a
  Vite/ESM project. Consequence: `npm run knip` now lists all ~20 barrel files
  under "Unused files" — that's expected given they have no consumers yet, not a
  regression to chase down. The `types/index.ts` barrel also disambiguates a real
  naming collision: both `billing.types.ts` and `guest.types.ts` export a
  `DocumentType` (invoice type vs. identity-document type) — re-exported from the
  barrel as `BillingDocumentType`/`GuestDocumentType`; importing directly from
  either `.types` file still gets the plain `DocumentType` name.
- **Resolved (2026-09-02)**: accessibility rules are no longer duplicated.
  `CLAUDE.md` §Frontend, `CONTRIBUTING.md` §7, `backup/DECISIONS.md` §4.2, and
  `docs/COMPLIANCE_AUDIT_2026-08.md` §8 were all trimmed to point here; each keeps
  only what's genuinely specific to that file (DECISIONS keeps the historical
  baseline pointer, COMPLIANCE_AUDIT keeps the PDF/UA fiscal-document note).
- **Still open**: the high-contrast rule above isn't fully honored today.
  `[data-contrast="high"]` and `.dark[data-contrast="high"]` in `m3-base.css`
  define `primary`/`secondary`/`error`/`surface-*`/`outline-*`/glass tokens, but
  **not** `tertiary`, `on-tertiary`, `tertiary-container`, `on-tertiary-container`,
  `inverse-surface`, `inverse-on-surface`, `inverse-primary`, or `scrim`. Those
  fall back to the base `:root`/`.dark` values even in high-contrast mode. Not
  fixed by this update — flagged here so it isn't lost.
- **Resolved (2026-09-02): Tailwind 3→4 migration complete**, via the official
  `@tailwindcss/upgrade` codemod (`backup/DECISIONS.md` §7.1 updated accordingly).
  The `var(--md-*)` token indirection now lives in an `@theme` block in
  `src/index.css` (colors/fonts/shadows/radii/keyframes), with `@custom-variant
  dark (&:is(.dark *));` preserving class-based dark mode. `tailwind.config.js` is
  gone. The codemod also renamed utilities across ~48 template files:
  `outline-none`→`outline-hidden`, bare `rounded`→`rounded-sm`,
  `flex-shrink-0`/`flex-grow`→`shrink-0`/`grow`, and added an explicit
  `border-color: var(--color-gray-200, currentcolor)` compatibility shim (v4
  changed the default border color to `currentcolor`). Build (`tsc -b && vite
  build`), lint, and the full Vitest suite (1062 tests) were verified green after
  migrating.

---

## Inspiration / prior art

The structure of this file draws on two external references reviewed while drafting
it — noted here for context, not because this repo adopts either directly:

- Vercel's internal practice of maintaining a `design.md` alongside agent-built
  pages, so coding agents have an explicit, machine-readable design contract instead
  of drifting toward generic output ("How our agents build on-brand pages with
  design.md").
- Meta's **Astryx** (open-sourced June 2026), an AI-agent-ready React design system
  — this repo does not use Astryx's component library, but borrows its stated
  authoring principles: *guidance over enforcement*, *strong documented
  conventions*, *one system for humans and AI*, and *earned by measurement*
  (conventions should be tested, not just asserted — hence `vitest-axe` everywhere
  and the explicit Known Gaps section above instead of silent aspiration).
