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
} as const;
