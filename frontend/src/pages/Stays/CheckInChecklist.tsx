import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormatters } from '../../hooks/useFormatters';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3';
import type { ChecklistItem, ChecklistTone } from './checkInReadiness';

const TONE_ICON: Record<ChecklistTone, string> = {
  ok: 'check_circle',
  warn: 'warning',
  pending: 'radio_button_unchecked',
};

const TONE_CLASS: Record<ChecklistTone, string> = {
  ok: 'text-tertiary',
  warn: 'text-secondary',
  pending: 'text-on-surface-variant',
};

interface CheckInChecklistProps {
  items: ChecklistItem[];
}

export const CheckInChecklist = memo(({ items }: CheckInChecklistProps) => {
  const { t } = useTranslation('stays');
  const { formatDate } = useFormatters();
  const detailParams = (item: ChecklistItem) => {
    const key = item.dateParam;
    return key && item.detailParams ? { ...item.detailParams, [key]: formatDate(String(item.detailParams[key])) } : item.detailParams;
  };
  const done = items.filter((i) => i.tone === 'ok').length;

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <h2 className="text-xl font-display font-medium text-on-surface">{t('checklist_title')}</h2>
      <p className="text-sm text-on-surface-variant" role="status">
        {t('checklist_progress', { done, total: items.length })}
      </p>
      <ul className="space-y-3">
        {items.map((item) => (
          <li key={item.id} className="flex items-start gap-3">
            <MaterialIcon name={TONE_ICON[item.tone]} className={TONE_CLASS[item.tone]} />
            <div className="min-w-0">
              <span className="sr-only">{t(`checklist_tone_${item.tone}`)}: </span>
              <p className="text-sm font-medium text-on-surface">{t(item.titleKey, item.titleParams)}</p>
              {item.detailKey && (
                <p className="text-xs text-on-surface-variant">{t(item.detailKey, detailParams(item))}</p>
              )}
            </div>
          </li>
        ))}
      </ul>
    </M3Card>
  );
});
CheckInChecklist.displayName = 'CheckInChecklist';
