import { MaterialIcon } from './MaterialIcon';
import { useToastStore } from '../store';
import type { Toast, ToastType } from '../store';
import { useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../utils';

const iconByType: Record<ToastType, string> = {
  success: 'check_circle',
  error: 'cancel',
  info: 'info',
};

const toneByType: Record<ToastType, string> = {
  success: 'bg-tertiary-container border-tertiary text-on-tertiary-container',
  error: 'bg-error-container border-error text-on-error-container',
  info: 'bg-primary-container border-primary text-on-primary-container',
};

const ToastItem = memo(({ toast }: { toast: Toast }) => {
  const removeToast = useToastStore((s) => s.removeToast);
  const { t } = useTranslation('common');

  const handleRemove = useCallback(() => {
    removeToast(toast.id);
  }, [removeToast, toast.id]);

  return (
    <div
      className={cn(
        'flex items-start gap-3 px-4 py-3 rounded-shape-md border shadow-elevation-2 min-w-[280px] max-w-sm animate-fade-in',
        toneByType[toast.type]
      )}
      role="alert"
    >
      <MaterialIcon name={iconByType[toast.type]} size={20} className="shrink-0 mt-0.5" />
      <p className="text-sm font-body flex-1">{toast.message}</p>
      <button
        onClick={handleRemove}
        className="shrink-0 flex items-center justify-center min-h-[40px] min-w-[40px] -my-2.5 -mr-2 opacity-60 hover:opacity-100 transition-opacity rounded-sm focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-current focus-visible:ring-offset-1 focus-visible:outline-hidden"
        aria-label={t('dismiss_notification')}
      >
        <MaterialIcon name="close" size={16} />
      </button>
    </div>
  );
});

ToastItem.displayName = 'ToastItem';

export const ToastContainer = () => {
  const toasts = useToastStore((s) => s.toasts);
  const assertiveToasts = toasts.filter((toast) => toast.type === 'error');
  const politeToasts = toasts.filter((toast) => toast.type !== 'error');

  // Both live regions stay mounted even when empty. An `aria-live` region
  // that doesn't exist yet at the moment a toast is added is not observed
  // by the screen reader, so unmounting the whole container between toasts
  // (the previous `if (toasts.length === 0) return null`) meant the very
  // first toast after a quiet period could go unannounced. Errors get
  // `assertive` (interrupts immediately); success/info get `polite`.
  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2">
      <div aria-live="assertive" aria-atomic="false" className="flex flex-col gap-2">
        {assertiveToasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </div>
      <div aria-live="polite" aria-atomic="false" className="flex flex-col gap-2">
        {politeToasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </div>
    </div>
  );
};
