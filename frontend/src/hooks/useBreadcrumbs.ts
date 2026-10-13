import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { Crumb } from '../components/Breadcrumbs';
import { resolveCrumbs } from '../config/navigation';

/** Translated breadcrumb trail for the current route. `leafLabel` replaces the
 * last crumb's label for pages whose name is data (a group, a quotation). */
export const useBreadcrumbs = (leafLabel?: string): Crumb[] => {
  const { pathname } = useLocation();
  const { t } = useTranslation();

  return useMemo(
    () =>
      resolveCrumbs(pathname).map((spec, index, all) => ({
        label: index === all.length - 1 && leafLabel ? leafLabel : t(spec.labelKey, { ns: spec.ns }),
        ...(spec.path ? { to: spec.path } : {}),
      })),
    [pathname, leafLabel, t],
  );
};
