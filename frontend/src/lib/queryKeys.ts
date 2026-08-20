/**
 * Central registry of TanStack Query cache keys, one factory per domain.
 * Keeping these in one place makes `invalidateQueries` calls verifiable at
 * a glance instead of relying on hand-typed key arrays scattered across
 * pages. Add a new domain's factory here as each area migrates off manual
 * `useEffect` fetching (see docs/plan Fase 0.1).
 */
export const queryKeys = {
  guests: {
    all: ['guests'] as const,
    search: (query: string, page: number, size: number) =>
      ['guests', 'search', query, page, size] as const,
    detail: (id: string) => ['guests', 'detail', id] as const,
  },
  rooms: {
    all: ['rooms'] as const,
    list: (availableOnly: boolean) => ['rooms', 'list', availableOnly] as const,
    /** Full unpaginated room list used as a lookup map (e.g. resolving room
     * numbers on the Reservations table) — distinct key from `list()` since
     * it fetches a different page size and callers shouldn't share a cache
     * entry that could silently swap between the two shapes. */
    lookup: ['rooms', 'lookup'] as const,
  },
  roomTypes: {
    all: ['room-types'] as const,
  },
  reservations: {
    all: ['reservations'] as const,
    search: (params: { query: string; upcomingOnly: boolean; page: number; sort: string }) =>
      ['reservations', 'search', params] as const,
  },
  invoices: {
    all: ['invoices'] as const,
    search: (params: { status?: string; query: string; dateFrom?: string; dateTo?: string; page: number }) =>
      ['invoices', 'search', params] as const,
  },
} as const;
