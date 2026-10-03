import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '../utils';
import { MaterialIcon } from './MaterialIcon';

export interface Crumb {
  label: string;
  /** Absent for a heading (the sidebar group) and ignored on the last crumb. */
  to?: string;
}

interface BreadcrumbsProps {
  crumbs: readonly Crumb[];
}

const LINK_CLASSES = cn(
  'inline-flex items-center min-h-10 px-1 rounded-shape-sm transition-colors',
  'hover:text-on-surface hover:underline',
  'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
);

/** Trail above a page title. A single crumb is just the title again, so
 * fewer than two renders nothing. The leading group heading is dropped
 * below `sm` to keep the trail on one line. */
export const Breadcrumbs = ({ crumbs }: BreadcrumbsProps) => {
  const { t } = useTranslation('common');

  if (crumbs.length < 2) return null;

  const lastIndex = crumbs.length - 1;
  // [group heading, current page]: with the heading dropped below `sm` the
  // trail would just repeat the h1, so the whole nav goes.
  const headingAndLeafOnly = crumbs.length === 2 && crumbs[0].to === undefined;

  return (
    <nav aria-label={t('breadcrumb_label')} className={cn('mb-1', headingAndLeafOnly && 'max-sm:hidden')}>
      <ol className="flex flex-wrap items-center text-sm font-body text-on-surface-variant">
        {crumbs.map((crumb, index) => {
          const isLast = index === lastIndex;
          const hiddenOnMobile = index === 0 && !crumb.to;
          return (
            <Fragment key={`${index}-${crumb.label}`}>
              <li className={cn('flex items-center min-w-0', hiddenOnMobile && 'max-sm:hidden')}>
                {index > 0 && (
                  <MaterialIcon
                    name="chevron_right"
                    size={18}
                    className={cn('shrink-0', index === 1 && crumbs[0].to === undefined && 'max-sm:hidden')}
                  />
                )}
                {isLast ? (
                  <span aria-current="page" className="px-1 font-medium text-on-surface truncate">
                    {crumb.label}
                  </span>
                ) : crumb.to ? (
                  <Link to={crumb.to} className={LINK_CLASSES}>
                    {crumb.label}
                  </Link>
                ) : (
                  <span className="px-1">{crumb.label}</span>
                )}
              </li>
            </Fragment>
          );
        })}
      </ol>
    </nav>
  );
};
