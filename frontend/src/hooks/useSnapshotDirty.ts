import { useCallback, useState } from 'react';

/**
 * Tells whether a form differs from how it started. `snapshot` is a string that changes when any
 * edit does (e.g. `JSON.stringify` of the fields); the baseline is the first snapshot seen while
 * `ready` is true, so data loaded after mount counts as the starting point, not as an edit.
 * `markSaved` makes the current snapshot the new baseline.
 */
export function useSnapshotDirty(snapshot: string, ready: boolean) {
  const [baseline, setBaseline] = useState<string | null>(null);

  // State adjusted during render: the baseline is fixed by the first ready render.
  if (ready && baseline === null) setBaseline(snapshot);

  const markSaved = useCallback(() => setBaseline(snapshot), [snapshot]);

  return { dirty: ready && baseline !== null && snapshot !== baseline, markSaved };
}
