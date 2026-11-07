import { useQuery } from '@tanstack/react-query';
import { quotationService } from '../../services';
import { queryKeys } from '../../lib';

/** One quotation with its options; idle while the route has no id. */
export function useQuotation(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.quotations.detail(id ?? ''),
    queryFn: () => quotationService.getQuotationById(id as string),
    enabled: Boolean(id),
  });
}
