import type { StatusTone } from './domainStatus';

// Look of each semantic tone in every shape a status takes in the UI. Which tone
// a status gets lives in domainStatus.ts; this file only decides how a tone
// looks, so a room or reservation status can't drift between a chip, a card and
// a calendar bar. Class strings stay literal so Tailwind can see them.

/** Tag-shaped chip (M3StatusChip). */
export const toneChipClasses: Record<StatusTone, string> = {
  success: 'bg-tertiary-container text-on-tertiary-container',
  warning: 'bg-secondary-container text-on-secondary-container',
  error: 'bg-error-container text-on-error-container',
  info: 'bg-primary-container text-on-primary-container',
  neutral: 'bg-surface-container-highest text-on-surface-variant',
};

/** Selectable card with a full border (Housekeeping cards, active filter badge). */
export const toneCardClasses: Record<StatusTone, string> = {
  success: 'bg-tertiary-container/30 border-tertiary',
  warning: 'bg-secondary-container/30 border-secondary',
  error: 'bg-error-container/30 border-error',
  info: 'bg-primary-container/30 border-primary',
  neutral: 'bg-surface-container-highest/30 border-outline',
};

/** Outlined action button that sets a status (Housekeeping). */
export const toneOutlineButtonClasses: Record<StatusTone, string> = {
  success: 'border-tertiary text-tertiary hover:bg-tertiary-container',
  warning: 'border-secondary text-secondary hover:bg-secondary-container',
  error: 'border-error text-error hover:bg-error-container',
  info: 'border-primary text-primary hover:bg-primary-container',
  neutral: 'border-outline text-on-surface-variant hover:bg-surface-container',
};

/** Solid block with its own text colour (planning bars). Neutral is a tonal bar
 * with an outline: text on `outline` has no contrast in the high-contrast themes,
 * where `outline` and `on-surface` are the same colour. */
export const toneSolidClasses: Record<StatusTone, string> = {
  success: 'bg-tertiary text-on-tertiary',
  warning: 'bg-secondary text-on-secondary',
  error: 'bg-error text-on-error',
  info: 'bg-primary text-on-primary',
  neutral: 'bg-surface-container-highest text-on-surface border border-outline',
};

/** Small status dot. */
export const toneDotClasses: Record<StatusTone, string> = {
  success: 'bg-tertiary',
  warning: 'bg-secondary',
  error: 'bg-error',
  info: 'bg-primary',
  neutral: 'bg-outline',
};

/** `toneSolidClasses` as custom-property names, for react-big-calendar's inline
 * styles (resolve with `resolveDesignToken`). */
export const toneSolidTokens: Record<StatusTone, { bg: string; text: string; border: string }> = {
  success: { bg: '--md-tertiary', text: '--md-on-tertiary', border: '--md-tertiary' },
  warning: { bg: '--md-secondary', text: '--md-on-secondary', border: '--md-secondary' },
  error: { bg: '--md-error', text: '--md-on-error', border: '--md-error' },
  info: { bg: '--md-primary', text: '--md-on-primary', border: '--md-primary' },
  neutral: { bg: '--md-surface-container-highest', text: '--md-on-surface', border: '--md-outline' },
};
