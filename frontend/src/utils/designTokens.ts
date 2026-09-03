/**
 * Reads a CSS custom property's live computed value (e.g. `resolveDesignToken('--md-primary')`)
 * from the document root. For color values that must be handed to a third-party
 * library as an inline style/prop instead of a className (react-big-calendar's
 * `eventPropGetter`, recharts series colors, ...) -- resolved at call time, so it
 * reflects the current theme (light/dark) and `[data-contrast="high"]` state
 * correctly instead of baking one hex value in at build time.
 */
export function resolveDesignToken(cssVarName: string): string {
  if (typeof window === 'undefined') return '';
  return getComputedStyle(document.documentElement).getPropertyValue(cssVarName).trim();
}
