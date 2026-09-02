import { useQuery, useQueryClient } from '@tanstack/react-query';
import { billingService } from '../../services/billingService';
import type { InvoiceResponse, InvoiceSearchResult, InvoiceStatus } from '../../types';
import type { SpringPage } from '../../types';
import { queryKeys } from '../../lib/queryKeys';

interface SearchParams {
  status?: InvoiceStatus;
  query: string;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  size: number;
  sort?: string;
}

export function useInvoicesSearch(params: SearchParams) {
  return useQuery({
    queryKey: queryKeys.invoices.search(params),
    queryFn: () => billingService.searchInvoices(params),
    placeholderData: (previousData) => previousData,
  });
}

/**
 * PaymentModal and InvoiceDetailModal call billingService directly and hand
 * back the updated invoice via a callback rather than going through a
 * mutation here — this patches that invoice into whichever cached
 * search-result page it's in, the same "patch in place, don't refetch"
 * choice as Reservations' retry-confirmation-email.
 */
export function usePatchInvoiceInCache() {
  const queryClient = useQueryClient();
  return (updated: InvoiceResponse) => {
    queryClient.setQueriesData<SpringPage<InvoiceSearchResult>>(
      { queryKey: queryKeys.invoices.all },
      (old) => old && {
        ...old,
        content: old.content.map((r) => (r.invoice.id === updated.id ? { ...r, invoice: updated } : r)),
      },
    );
  };
}
