import type { AlertStatus, AuthOutcome, RiskLevel } from '../types/api';
import { titleCase } from '../api/format';

// Each badge pairs colour with an icon shape and a text label, so meaning never
// depends on colour alone.
const RISK_ICON: Record<RiskLevel, string> = { LOW: '●', MEDIUM: '▲', HIGH: '◆' };

export function RiskBadge({ level, score }: { level: RiskLevel | null; score?: number | null }) {
  if (!level) return <span className="badge badge-muted">Not assessed</span>;
  return (
    <span className={`badge risk-${level.toLowerCase()}`}>
      <span aria-hidden="true">{RISK_ICON[level]}</span>
      {titleCase(level)}
      {score != null && <span className="badge-score">{score}</span>}
    </span>
  );
}

const STATUS_ICON: Record<AlertStatus, string> = {
  OPEN: '○',
  INVESTIGATING: '◐',
  RESOLVED: '✓',
  DISMISSED: '–',
};

export function StatusBadge({ status }: { status: AlertStatus | null }) {
  if (!status) return <span className="badge badge-muted">No alert</span>;
  return (
    <span className={`badge status-${status.toLowerCase()}`}>
      <span aria-hidden="true">{STATUS_ICON[status]}</span>
      {titleCase(status)}
    </span>
  );
}

export function OutcomeTag({ outcome }: { outcome: AuthOutcome }) {
  return <span className={`outcome outcome-${outcome.toLowerCase()}`}>{titleCase(outcome)}</span>;
}

export function RuleChip({ code, title }: { code: string; title?: string }) {
  return (
    <span className="rule-chip" title={title}>
      {code}
    </span>
  );
}
