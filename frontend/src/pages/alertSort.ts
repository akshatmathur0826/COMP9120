import type { AlertSummary, RiskLevel } from '../types/api';

const RISK_RANK: Record<RiskLevel, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 };
const time = (iso: string) => Date.parse(iso);

// Same ordering as query Q6: highest risk first, then oldest alert first.
export function byPriority(a: AlertSummary, b: AlertSummary) {
  return (
    RISK_RANK[b.riskLevel] - RISK_RANK[a.riskLevel] ||
    time(a.createdAt) - time(b.createdAt) ||
    a.alertId - b.alertId
  );
}

export function byNewest(a: AlertSummary, b: AlertSummary) {
  return time(b.createdAt) - time(a.createdAt) || b.alertId - a.alertId;
}
