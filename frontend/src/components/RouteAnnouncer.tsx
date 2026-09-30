import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { resolveAnnouncement } from '../config/navigation';

/**
 * Announces navigation to assistive technology. A client-side route change
 * doesn't trigger a full page load, so a screen reader has nothing telling
 * it the view changed — this was the app's only unannounced-navigation gap
 * (docs/COMPLIANCE_AUDIT_2026-08.md claims WCAG 2.2 AA; before this, the
 * single other aria-live in the whole app was in OrderFormModal). Mounted
 * once in MainLayout, above the routed <Outlet>.
 */
export const RouteAnnouncer = () => {
  const location = useLocation();
  const { t } = useTranslation();
  const liveRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const entry = resolveAnnouncement(location.pathname);
    const label = t(entry.key, { ns: entry.ns });
    document.title = `${label} · Hotel PMS`;

    if (liveRef.current) {
      liveRef.current.textContent = label;
    }

    // Move focus to the main landmark so keyboard and screen-reader users
    // land somewhere meaningful after navigating, instead of focus staying
    // on the (now stale) nav link/button that triggered the navigation.
    // The skip link already targets this same element (MainLayout.tsx).
    document.getElementById('main-content')?.focus();
  }, [location.pathname, t]);

  return <div aria-live="polite" aria-atomic="true" className="sr-only" ref={liveRef} />;
};
