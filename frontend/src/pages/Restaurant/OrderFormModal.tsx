import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Button } from '../../components/m3/M3Button';
import { M3TextField } from '../../components/m3/M3TextField';
import { MaterialIcon } from '../../components/MaterialIcon';
import { fbService } from '../../services/fbService';
import { useToastStore } from '../../store/toastStore';
import type { MenuItemResponse } from '../../types/fb.types';

interface Props {
  onClose: () => void;
  onCreated: () => void;
}

interface MenuItemRowProps {
  item: MenuItemResponse;
  quantity: number;
  onDecrement: (itemId: string) => void;
  onIncrement: (itemId: string) => void;
  formatCurrency: (val: number) => string;
  t: (key: string) => string;
}

const MenuItemRow = memo(
  ({ item, quantity, onDecrement, onIncrement, formatCurrency, t }: MenuItemRowProps) => {
    const handleDecrement = useCallback(() => onDecrement(item.id), [onDecrement, item.id]);
    const handleIncrement = useCallback(() => onIncrement(item.id), [onIncrement, item.id]);

    return (
      <li className="flex items-center justify-between rounded-shape-xs bg-surface-container px-4 py-2">
        <div className="flex-1 min-w-0">
          <span className="block text-sm font-medium font-body text-on-surface">{item.name}</span>
          <span className="block text-xs font-body text-on-surface-variant">
            {formatCurrency(item.price)}
          </span>
        </div>
        <div
          className="flex items-center gap-3 ml-4"
          role="group"
          aria-label={`${item.name} ${t('quantity')}`}
        >
          <button
            type="button"
            onClick={handleDecrement}
            disabled={quantity === 0}
            aria-label={`${t('decrease_quantity')} ${item.name}`}
            className="w-10 h-10 flex items-center justify-center rounded-full text-primary hover:bg-primary/10 disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 transition-colors"
          >
            <MaterialIcon name="remove" size={18} aria-hidden="true" />
          </button>
          <span
            className="w-6 text-center text-sm font-body font-medium text-on-surface tabular-nums"
            aria-live="polite"
          >
            {quantity}
          </span>
          <button
            type="button"
            onClick={handleIncrement}
            aria-label={`${t('increase_quantity')} ${item.name}`}
            className="w-10 h-10 flex items-center justify-center rounded-full text-primary hover:bg-primary/10 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 transition-colors"
          >
            <MaterialIcon name="add" size={18} aria-hidden="true" />
          </button>
        </div>
      </li>
    );
  },
);

MenuItemRow.displayName = 'MenuItemRow';

export const OrderFormModal = memo(({ onClose, onCreated }: Props) => {
  const { t, i18n } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);

  const [stayId, setStayId] = useState('');
  const [menuItems, setMenuItems] = useState<MenuItemResponse[]>([]);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [loadingMenu, setLoadingMenu] = useState(true);
  const [stayIdError, setStayIdError] = useState('');

  useEffect(() => {
    fbService
      .getMenuItems()
      .then(setMenuItems)
      .catch(() => {})
      .finally(() => setLoadingMenu(false));
  }, []);

  const formatCurrency = useCallback(
    (val: number) =>
      new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(val),
    [i18n.language],
  );

  const handleStayIdChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setStayId(e.target.value);
    setStayIdError('');
  }, []);

  const handleDecrement = useCallback((itemId: string) => {
    setQuantities((prev) => {
      const current = prev[itemId] ?? 0;
      const next = Math.max(0, current - 1);
      if (next === 0) {
        const updated = { ...prev };
        delete updated[itemId];
        return updated;
      }
      return { ...prev, [itemId]: next };
    });
  }, []);

  const handleIncrement = useCallback((itemId: string) => {
    setQuantities((prev) => ({ ...prev, [itemId]: (prev[itemId] ?? 0) + 1 }));
  }, []);

  const total = useMemo(
    () =>
      menuItems.reduce((sum, item) => {
        const qty = quantities[item.id] ?? 0;
        return sum + qty * item.price;
      }, 0),
    [menuItems, quantities],
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const trimmedStayId = stayId.trim();
      if (!trimmedStayId) {
        setStayIdError(t('err_stay_id_required'));
        return;
      }
      const items = Object.entries(quantities)
        .filter(([, qty]) => qty > 0)
        .map(([menuItemId, quantity]) => ({ menuItemId, quantity }));
      if (items.length === 0) {
        addToast(t('no_items_selected'), 'error');
        return;
      }
      setLoading(true);
      try {
        await fbService.createOrder({ stayId: trimmedStayId, items });
        addToast(t('order_created_success'), 'success');
        onCreated();
        onClose();
      } catch {
        addToast(t('order_creation_failed'), 'error');
      } finally {
        setLoading(false);
      }
    },
    [stayId, quantities, t, addToast, onCreated, onClose],
  );

  return (
    <M3Dialog
      open
      title={t('order_form_title')}
      titleId="order-form-modal-title"
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <M3TextField
          label={t('stay_id_label')}
          type="text"
          value={stayId}
          onChange={handleStayIdChange}
          required
          errorText={stayIdError || undefined}
        />

        <div>
          <p className="text-sm font-medium font-body text-on-surface mb-3" id="menu-items-label">
            {t('select_menu_items')}
          </p>

          {loadingMenu ? (
            <div className="flex justify-center py-6" aria-label={t('loading')}>
              <MaterialIcon
                name="progress_activity"
                size={24}
                className="text-primary animate-spin"
                aria-hidden="true"
              />
            </div>
          ) : menuItems.length === 0 ? (
            <p className="text-sm font-body text-on-surface-variant text-center py-4">
              {t('no_menu_available')}
            </p>
          ) : (
            <ul aria-labelledby="menu-items-label" className="space-y-2">
              {menuItems.map((item) => (
                <MenuItemRow
                  key={item.id}
                  item={item}
                  quantity={quantities[item.id] ?? 0}
                  onDecrement={handleDecrement}
                  onIncrement={handleIncrement}
                  formatCurrency={formatCurrency}
                  t={t}
                />
              ))}
            </ul>
          )}
        </div>

        {total > 0 && (
          <div className="rounded-shape-sm bg-secondary-container px-4 py-3 text-sm font-body">
            <span className="text-on-secondary-container font-medium">
              {t('order_total_preview')}: {formatCurrency(total)}
            </span>
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <M3Button type="button" variant="outlined" onClick={onClose} disabled={loading}>
            {t('cancel')}
          </M3Button>
          <M3Button type="submit" loading={loading} icon="restaurant_menu">
            {t('create_order')}
          </M3Button>
        </div>
      </form>
    </M3Dialog>
  );
});

OrderFormModal.displayName = 'OrderFormModal';
