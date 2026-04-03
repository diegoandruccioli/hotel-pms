import { MaterialIcon } from './MaterialIcon';
import { useToastStore } from '../store/toastStore';
import type { Toast, ToastType } from '../store/toastStore';
import { useCallback, memo } from 'react';

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

  const handleRemove = useCallback(() => {
    removeToast(toast.id);
  }, [removeToast, toast.id]);

  return (
    <div
      className={`flex items-start gap-3 px-4 py-3 rounded-shape-md border shadow-elevation-2 min-w-[280px] max-w-sm animate-fade-in ${toneByType[toast.type]}`}
      role="alert"
    >
      <MaterialIcon name={iconByType[toast.type]} size={20} className="flex-shrink-0 mt-0.5" />
      <p className="text-sm font-body flex-1">{toast.message}</p>
      <button
        onClick={handleRemove}
        className="opacity-60 hover:opacity-100 transition-opacity"
        aria-label="Dismiss notification"
      >
        <MaterialIcon name="close" size={16} />
      </button>
    </div>
  );
});

ToastItem.displayName = 'ToastItem';

export const ToastContainer = () => {
  const toasts = useToastStore((s) => s.toasts);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
};
