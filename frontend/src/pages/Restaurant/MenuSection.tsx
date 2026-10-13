import { useState, useCallback, memo, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import type { MenuItemResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import {
  M3Button, M3Card, M3ConfirmDialog, M3DataTable, M3EmptyState, M3StatusChip, M3TableActionLink,
} from '../../components/m3';
import { useFormatters } from '../../hooks';
import { useMenuItems, useDeleteMenuItem } from '../../hooks/queries';
import { queryKeys } from '../../lib';
import { useToastStore } from '../../store';
import { getErrorMessage } from '../../utils';
import { MenuFormModal } from './MenuFormModal';

const EMPTY_MENU_ITEMS: MenuItemResponse[] = [];
const DEFAULT_MENU_SORT_FIELD = 'name';

interface MenuActionsCellProps {
  mi: MenuItemResponse;
  onEdit: (mi: MenuItemResponse) => void;
  onDelete: (mi: MenuItemResponse) => void;
  tLabel: (key: string) => string;
  tCommon: (key: string) => string;
}

const MenuActionsCell = ({ mi, onEdit, onDelete, tLabel, tCommon }: MenuActionsCellProps) => {
  const handleEdit = useCallback(() => onEdit(mi), [onEdit, mi]);
  const handleDelete = useCallback(() => onDelete(mi), [onDelete, mi]);
  return (
    <div className="flex justify-end gap-2">
      <M3TableActionLink onClick={handleEdit} aria-label={`${tLabel('menu_edit_item')} ${mi.name}`}>
        {tCommon('edit')}
      </M3TableActionLink>
      <M3TableActionLink tone="error" onClick={handleDelete} aria-label={`${tLabel('menu_delete_item')} ${mi.name}`}>
        {tCommon('delete')}
      </M3TableActionLink>
    </div>
  );
};

function compareMenuItems(a: MenuItemResponse, b: MenuItemResponse, field: string): number {
  switch (field) {
    case 'category': return a.category.localeCompare(b.category);
    case 'price': return a.price - b.price;
    case 'available': return Number(a.available) - Number(b.available);
    default: return a.name.localeCompare(b.name);
  }
}

/** Menu management card of the restaurant page. Mount it for ADMIN/OWNER only:
 * it fetches the menu on mount. */
export const MenuSection = memo(() => {
  const { t } = useTranslation('common');
  const { t: tMenu } = useTranslation('restaurant');
  const { addToast } = useToastStore();
  const queryClient = useQueryClient();
  const { formatCurrency } = useFormatters();

  const [menuFormTarget, setMenuFormTarget] = useState<MenuItemResponse | 'new' | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MenuItemResponse | null>(null);
  const [sortField, setSortField] = useState(DEFAULT_MENU_SORT_FIELD);
  const [sortDesc, setSortDesc] = useState(false);

  const { data: menuItemsData } = useMenuItems(true);
  const menuItems = menuItemsData ?? EMPTY_MENU_ITEMS;

  const handleMenuSaved = useCallback(() => {
    setMenuFormTarget(null);
    queryClient.invalidateQueries({ queryKey: queryKeys.menuItems.all });
  }, [queryClient]);

  const handleMenuEdit = useCallback((mi: MenuItemResponse) => setMenuFormTarget(mi), []);
  const handleOpenMenuForm = useCallback(() => setMenuFormTarget('new'), []);
  const handleCloseMenuForm = useCallback(() => setMenuFormTarget(null), []);

  const deleteMenuItemMutation = useDeleteMenuItem();
  const handleDeleteRequest = useCallback((item: MenuItemResponse) => setDeleteTarget(item), []);
  const handleDeleteCancel = useCallback(() => {
    // Escape and scrim clicks land here too: don't close under a running request.
    if (deleteMenuItemMutation.isPending) return;
    setDeleteTarget(null);
  }, [deleteMenuItemMutation.isPending]);
  const handleDeleteConfirm = useCallback(async () => {
    if (!deleteTarget) return;
    try {
      await deleteMenuItemMutation.mutateAsync(deleteTarget.id);
      addToast(tMenu('menu_delete_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, tMenu('menu_delete_error')), 'error');
    } finally {
      setDeleteTarget(null);
    }
  }, [addToast, deleteMenuItemMutation, deleteTarget, tMenu]);

  const sorting = useMemo<SortingState>(() => [{ id: sortField, desc: sortDesc }], [sortField, sortDesc]);
  const handleSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id);
    setSortDesc(next[0].desc);
  }, []);

  const sortedMenuItems = useMemo(() => {
    const sign = sortDesc ? -1 : 1;
    return [...menuItems].sort((a, b) => sign * compareMenuItems(a, b, sortField));
  }, [menuItems, sortField, sortDesc]);

  const getMenuItemRowId = useCallback((mi: MenuItemResponse) => mi.id, []);

  const columns = useMemo<ColumnDef<MenuItemResponse>[]>(() => [
    {
      id: 'name',
      accessorKey: 'name',
      header: tMenu('menu_name'),
      cell: ({ row }) => <span className="font-medium">{row.original.name}</span>,
    },
    {
      id: 'category',
      accessorKey: 'category',
      header: tMenu('menu_category'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.category}</span>,
    },
    {
      id: 'price',
      accessorKey: 'price',
      header: tMenu('menu_price'),
      cell: ({ row }) => <span className="text-right block">{formatCurrency(row.original.price)}</span>,
    },
    {
      id: 'available',
      accessorKey: 'available',
      header: tMenu('menu_available'),
      cell: ({ row }) => (
        <div className="text-center">
          <M3StatusChip
            label={row.original.available ? tMenu('menu_available_yes') : tMenu('menu_available_no')}
            tone={row.original.available ? 'success' : 'neutral'}
          />
        </div>
      ),
    },
    {
      id: 'actions',
      header: t('actions'),
      enableSorting: false,
      cell: ({ row }) => (
        <MenuActionsCell
          mi={row.original}
          onEdit={handleMenuEdit}
          onDelete={handleDeleteRequest}
          tLabel={tMenu}
          tCommon={t}
        />
      ),
    },
  ], [t, tMenu, formatCurrency, handleMenuEdit, handleDeleteRequest]);

  return (
    <>
      <M3Card variant="solid" className="p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <MaterialIcon name="menu_book" size={20} className="text-primary" />
            <h2 className="text-sm font-display font-semibold text-on-surface">{tMenu('menu_title')}</h2>
          </div>
          <M3Button icon="add" variant="tonal" onClick={handleOpenMenuForm}>
            {tMenu('menu_add_item')}
          </M3Button>
        </div>
        {menuItems.length === 0 ? (
          <M3EmptyState icon="menu_book" title={tMenu('menu_no_items')} className="py-6" />
        ) : (
          <M3DataTable
            data={sortedMenuItems}
            columns={columns}
            getRowId={getMenuItemRowId}
            sorting={sorting}
            onSortingChange={handleSortingChange}
            emptyMessage={tMenu('menu_no_items')}
          />
        )}
      </M3Card>

      {menuFormTarget && (
        <MenuFormModal
          item={menuFormTarget === 'new' ? undefined : menuFormTarget}
          onClose={handleCloseMenuForm}
          onSaved={handleMenuSaved}
        />
      )}

      {deleteTarget && (
        <M3ConfirmDialog
          title={tMenu('menu_delete_item')}
          titleId="confirm-delete-menu-item-dialog"
          message={tMenu('menu_delete_confirm', { name: deleteTarget.name })}
          confirmLabel={t('delete')}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          loading={deleteMenuItemMutation.isPending}
        />
      )}
    </>
  );
});

MenuSection.displayName = 'MenuSection';
