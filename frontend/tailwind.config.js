/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      /* ── M3 Color Tokens (CSS variable–driven for dark mode) ── */
      colors: {
        primary: {
          DEFAULT: "var(--md-primary)",
          container: "var(--md-primary-container)",
        },
        "on-primary": {
          DEFAULT: "var(--md-on-primary)",
          container: "var(--md-on-primary-container)",
        },
        secondary: {
          DEFAULT: "var(--md-secondary)",
          container: "var(--md-secondary-container)",
        },
        "on-secondary": {
          DEFAULT: "var(--md-on-secondary)",
          container: "var(--md-on-secondary-container)",
        },
        tertiary: {
          DEFAULT: "var(--md-tertiary)",
          container: "var(--md-tertiary-container)",
        },
        "on-tertiary": {
          DEFAULT: "var(--md-on-tertiary)",
          container: "var(--md-on-tertiary-container)",
        },
        error: {
          DEFAULT: "var(--md-error)",
          container: "var(--md-error-container)",
        },
        "on-error": {
          DEFAULT: "var(--md-on-error)",
          container: "var(--md-on-error-container)",
        },
        surface: {
          DEFAULT: "var(--md-surface)",
          variant: "var(--md-surface-variant)",
          dim: "var(--md-surface-dim)",
          bright: "var(--md-surface-bright)",
          "container-lowest": "var(--md-surface-container-lowest)",
          "container-low": "var(--md-surface-container-low)",
          container: "var(--md-surface-container)",
          "container-high": "var(--md-surface-container-high)",
          "container-highest": "var(--md-surface-container-highest)",
        },
        "on-surface": {
          DEFAULT: "var(--md-on-surface)",
          variant: "var(--md-on-surface-variant)",
        },
        outline: {
          DEFAULT: "var(--md-outline)",
          variant: "var(--md-outline-variant)",
        },
        inverse: {
          surface: "var(--md-inverse-surface)",
          "on-surface": "var(--md-inverse-on-surface)",
          primary: "var(--md-inverse-primary)",
        },
        scrim: "#000000",
      },

      /* ── Typography ──────────────────────────────────── */
      fontFamily: {
        display: ["Outfit", "system-ui", "sans-serif"],
        body: ["Inter", "system-ui", "sans-serif"],
      },

      /* ── M3 Elevation (tonal shadows) ────────────────── */
      boxShadow: {
        "elevation-0": "none",
        "elevation-1": "var(--md-elevation-1)",
        "elevation-2": "var(--md-elevation-2)",
        "elevation-3": "var(--md-elevation-3)",
        "elevation-4": "var(--md-elevation-4)",
        "elevation-5": "var(--md-elevation-5)",
      },

      /* ── M3 Shape Scale ──────────────────────────────── */
      borderRadius: {
        "shape-xs": "4px",
        "shape-sm": "8px",
        "shape-md": "12px",
        "shape-lg": "16px",
        "shape-xl": "28px",
        "shape-full": "9999px",
      },

      /* ── Keyframe Animations ─────────────────────────── */
      keyframes: {
        "fade-in": {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "slide-in-right": {
          from: { transform: "translateX(-100%)" },
          to: { transform: "translateX(0)" },
        },
        "slide-out-left": {
          from: { transform: "translateX(0)" },
          to: { transform: "translateX(-100%)" },
        },
        "scale-in": {
          from: { opacity: "0", transform: "scale(0.95)" },
          to: { opacity: "1", transform: "scale(1)" },
        },
      },
      animation: {
        "fade-in": "fade-in 200ms ease-out",
        "slide-in-right": "slide-in-right 300ms cubic-bezier(0.2,0,0,1)",
        "slide-out-left": "slide-out-left 250ms cubic-bezier(0.2,0,0,1)",
        "scale-in": "scale-in 200ms ease-out",
      },
    },
  },
  plugins: [],
};
