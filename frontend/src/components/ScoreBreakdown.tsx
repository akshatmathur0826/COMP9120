import type { MatchedRule, ReferenceData, RiskLevel } from '../types/api';
import { RiskBadge } from './Badges';

// Visualises how the stored score is the sum of matched rule weights, against the
// LOW 0-29 / MEDIUM 30-59 / HIGH 60+ bands from the scoring contract.
export function ScoreBreakdown({
  score,
  riskLevel,
  matches,
  rules,
}: {
  score: number;
  riskLevel: RiskLevel;
  matches: MatchedRule[];
  rules: ReferenceData['rules'] | undefined;
}) {
  const scaleMax = Math.max(100, score);
  const weightOf = (code: string) => rules?.find((r) => r.ruleCode === code)?.severityWeight;

  return (
    <div className="score">
      <div className="score-head">
        <div>
          <div className="score-value">{score}</div>
          <div className="muted">risk score</div>
        </div>
        <RiskBadge level={riskLevel} />
      </div>
      <div className="score-track" role="img" aria-label={`Score ${score} of ${scaleMax}; ${riskLevel} risk`}>
        <span className="band band-low" style={{ width: `${(30 / scaleMax) * 100}%` }} />
        <span className="band band-medium" style={{ left: `${(30 / scaleMax) * 100}%`, width: `${(30 / scaleMax) * 100}%` }} />
        <span className="band band-high" style={{ left: `${(60 / scaleMax) * 100}%`, right: 0 }} />
        <span className="score-marker" style={{ left: `${(Math.min(score, scaleMax) / scaleMax) * 100}%` }} />
      </div>
      <div className="score-scale" aria-hidden="true">
        <span style={{ left: 0 }}>0</span>
        <span style={{ left: `${(30 / scaleMax) * 100}%` }}>30 · alert</span>
        <span style={{ left: `${(60 / scaleMax) * 100}%` }}>60 · high</span>
      </div>
      {matches.length > 0 && (
        <p className="score-sum">
          {matches.map((m, i) => (
            <span key={m.ruleCode}>
              {i > 0 && ' + '}
              <strong>{m.ruleCode}</strong> {weightOf(m.ruleCode) ?? '?'}
            </span>
          ))}{' '}
          = {score}
        </p>
      )}
    </div>
  );
}

export function MatchedRuleList({
  matches,
  rules,
}: {
  matches: MatchedRule[];
  rules: ReferenceData['rules'] | undefined;
}) {
  if (matches.length === 0) {
    return <p className="muted">No rules matched. The assessment is recorded with a score of 0.</p>;
  }
  return (
    <ol className="rule-list">
      {matches.map((m) => {
        const rule = rules?.find((r) => r.ruleCode === m.ruleCode);
        return (
          <li key={m.ruleCode} className="rule-item">
            <div className="rule-item-head">
              <span className="rule-chip">{m.ruleCode}</span>
              <strong>{m.title}</strong>
              {rule && <span className="rule-weight">+{rule.severityWeight}</span>}
            </div>
            <p>{m.explanation}</p>
            {rule && <p className="muted small">Rule: {rule.description}</p>}
          </li>
        );
      })}
    </ol>
  );
}
