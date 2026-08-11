import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { fbService } from '../../services/fbService';
import { useToastStore } from '../../store/toastStore';
import { M3Button } from '../../components/m3/M3Button';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { getErrorMessage } from '../../utils/errorMessage';
import type { MenuItemRequest, MenuItemResponse } from '../../types/fb.types';

interface Props {
  item?: MenuItemResponse;
  onClose: () => void;
  onSaved: () => void;
}

const EMPTY_FORM: MenuItemRequest = { name: '', price: 0, category: '', description: '', available: true };

function itemToForm(item: MenuItemResponse): MenuItemRequest {
  return { name: item.name, price: item.price, category: item.category, description: item.description ?? '', available: item.available };
}

const INPUT_CLASS = 'w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary';

export const MenuFormModal = memo(({ item, onClose, onSaved }: Props) => {
  const { t } = useTranslation(['restaurant', 'common']);
  const { addToast } = useToastStore();
  const [form, setForm] = useState<MenuItemRequest>(item ? itemToForm(item) : EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const isEdit = item != null;

  const handleName = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, name: e.target.value })), []);
  const handleCategory = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, category: e.target.value })), []);
  const handlePrice = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, price: parseFloat(e.target.value) || 0 })), []);
  const handleDescription = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) =>
    setForm((p) => ({ ...p, description: e.target.value })), []);
  const handleAvailable = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, available: e.target.checked })), []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!form.name.trim() || !form.category.trim() || form.price <= 0) {
      setError(t('menu_validation_required'));
      return;
    }
    setLoading(true);
    try {
      if (isEdit && item) {
        await fbService.updateMenuItem(item.id, form);
      } else {
        await fbService.createMenuItem(form);
      }
      addToast(t('menu_save_success'), 'success');
      onSaved();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('menu_save_error')), 'error');
    } finally {
      setLoading(false);
    }
  }, [form, isEdit, item, addToast, t, onSaved]);

  return (
    <M3Dialog
      open
      title={isEdit ? t('menu_edit_item') : t('menu_add_item')}
      titleId="menu-form-title"
      onClose={onClose}
      footer={
        <div className="flex justify-end gap-2">
          <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
          <M3Button form="menu-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
        </div>
      }
    >
      <form id="menu-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <div>
          <label htmlFor="menu-name" className="block text-sm font-medium text-on-surface mb-1">{t('menu_name')} *</label>
          <input id="menu-name" type="text" value={form.name} onChange={handleName} className={INPUT_CLASS} />
        </div>
        <div>
          <label htmlFor="menu-category" className="block text-sm font-medium text-on-surface mb-1">{t('menu_category')} *</label>
          <input id="menu-category" type="text" value={form.category} onChange={handleCategory} className={INPUT_CLASS} />
        </div>
        <div>
          <label htmlFor="menu-price" className="block text-sm font-medium text-on-surface mb-1">{t('menu_price')} *</label>
          <input id="menu-price" type="number" min="0.01" step="0.01" value={form.price} onChange={handlePrice} className={INPUT_CLASS} />
        </div>
        <div>
          <label htmlFor="menu-description" className="block text-sm font-medium text-on-surface mb-1">{t('menu_description')}</label>
          <textarea id="menu-description" value={form.description ?? ''} onChange={handleDescription} rows={3} className={INPUT_CLASS} />
        </div>
        <div className="flex items-center gap-3">
          <input id="menu-available" type="checkbox" checked={form.available} onChange={handleAvailable}
            className="h-4 w-4 rounded border-outline text-primary focus:ring-primary" />
          <label htmlFor="menu-available" className="text-sm font-medium text-on-surface">{t('menu_available')}</label>
        </div>

        {error && <p role="alert" className="text-sm text-error">{error}</p>}
      </form>
    </M3Dialog>
  );
});
MenuFormModal.displayName = 'MenuFormModal';
