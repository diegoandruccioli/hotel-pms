import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { fbService } from '../../services';
import { useToastStore } from '../../store';
import { M3Button } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3Textarea } from '../../components/m3';
import { M3Checkbox } from '../../components/m3';
import { getErrorMessage } from '../../utils';
import type { MenuItemRequest, MenuItemResponse } from '../../types';

interface Props {
  item?: MenuItemResponse;
  onClose: () => void;
  onSaved: () => void;
}

const EMPTY_FORM: MenuItemRequest = { name: '', price: 0, category: '', description: '', available: true };

function itemToForm(item: MenuItemResponse): MenuItemRequest {
  return { name: item.name, price: item.price, category: item.category, description: item.description ?? '', available: item.available };
}

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
        <M3TextField label={`${t('menu_name')} *`} required value={form.name} onChange={handleName} />
        <M3TextField label={`${t('menu_category')} *`} required value={form.category} onChange={handleCategory} />
        <M3TextField
          label={`${t('menu_price')} *`}
          required
          type="number"
          min="0.01"
          step="0.01"
          value={form.price}
          onChange={handlePrice}
        />
        <M3Textarea label={t('menu_description')} value={form.description ?? ''} onChange={handleDescription} rows={3} />
        <M3Checkbox checked={form.available} onChange={handleAvailable} label={t('menu_available')} />

        {error && <p role="alert" className="text-sm text-error">{error}</p>}
      </form>
    </M3Dialog>
  );
});
MenuFormModal.displayName = 'MenuFormModal';
