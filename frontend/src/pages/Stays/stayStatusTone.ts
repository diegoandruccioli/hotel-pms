import type { StayStatus } from '../../types';

// ---------------------------------------------------------------------------
// getStatusTone — maps a stay status to the M3StatusChip visual tone.
// ---------------------------------------------------------------------------
export const getStatusTone = (status: StayStatus) => {
  switch (status) {
    case 'CHECKED_IN': return 'success' as const;
    case 'CHECKED_OUT': return 'neutral' as const;
    case 'EXPECTED': return 'info' as const;
    default: return 'neutral' as const;
  }
};
