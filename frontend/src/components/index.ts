// DO NOT import from this barrel. ./ErrorBoundary wraps a class component
// with the withTranslation HOC at module scope (required -- error boundaries
// can't use the useTranslation hook), so evaluating this module runs
// react-i18next's withTranslation() the moment ANYTHING is imported from
// here, even a completely unrelated member. That broke 38 test files whose
// react-i18next mock only stubbed useTranslation (see frontend/DESIGN.md
// Known gaps and the standardization plan's Fase 2 tier 6 for the incident).
// Import each component directly from its own file instead
// (`./components/Toast`, `./components/UserMenu`, ...); this file exists
// only for consistency with every other src/ folder's barrel-export rule,
// not because it's safe to consume.
export * from './CommandPalette';
export * from './ErrorBoundary';
export * from './MaterialIcon';
export * from './PageHeader';
export * from './PasswordRequirementsChecklist';
export * from './ProtectedRoute';
export * from './RouteAnnouncer';
export * from './SettingsPageHeader';
export * from './StructuredAddressFields';
export * from './ThemeToggle';
export * from './Toast';
export * from './UserMenu';
