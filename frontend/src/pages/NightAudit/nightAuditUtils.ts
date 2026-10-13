import type { TFunction } from 'i18next';
import type { NightAuditRunResponse } from '../../types';

export const getStatusLabel = (status: NightAuditRunResponse['status'], t: TFunction) =>
  t(`night_audit_status_${status.toLowerCase()}`);

export const cashTotal = (run: NightAuditRunResponse): number =>
  run.cashByMethod.reduce((sum, line) => sum + line.total, 0);
