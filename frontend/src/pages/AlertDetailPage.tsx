import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatDateTime, titleCase } from '../api/format';
import { OutcomeTag, StatusBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { MatchedRuleList, ScoreBreakdown } from '../components/ScoreBreakdown';
import { Fact } from '../components/Fact';
import { ErrorBanner, Loading } from '../components/States';
import type { AlertDetail, AlertStatus, UpdateAlertStatusRequest } from '../types/api';

type NextStatus = UpdateAlertStatusRequest['newStatus'];

// Mirrors AlertReviewService.ALLOWED_TRANSITIONS; the server remains the authority.
const TRANSITIONS: Record<AlertStatus, NextStatus[]> = {
  OPEN: ['INVESTIGATING', 'DISMISSED'],
  INVESTIGATING: ['RESOLVED', 'DISMISSED'],
  RESOLVED: [],
  DISMISSED: [],
};

const ACTION_LABEL: Record<NextStatus, string> = {
  INVESTIGATING: 'Start investigating',
  RESOLVED: 'Resolve',
  DISMISSED: 'Dismiss',
};

const ANALYST_KEY = 'fraud-review.analystId';

export function AlertDetailPage() {
  const { id = '' } = useParams();
  const alert = useApi(() => api.alert(id), [id]);
  const reviews = useApi(() => api.alertReviews(id), [id]);
  const reference = useApi(api.reference);

  const [current, setCurrent] = useState<AlertDetail>();
  useEffect(() => setCurrent(alert.data), [alert.data]);

  if (alert.error) {
    return (
      <>
        <BackLink />
        <ErrorBanner error={alert.error} onRetry={alert.reload} />
      </>
    );
  }
  if (!current) return <Loading />;

  const a = current;

  return (
    <>
      <BackLink />
      <PageHeader
        title={
          <>
            Alert #{a.alertId} <StatusBadge status={a.status} />
          </>
        }
        subtitle={`Raised ${formatDateTime(a.createdAt)} · rule set ${a.ruleSetVersion}`}
      />

      <div className="detail-grid">
        <div className="stack">
          <section className="card">
            <h2>Why this was flagged</h2>
            <ScoreBreakdown score={a.score} riskLevel={a.riskLevel} matches={a.matchedRules} rules={reference.data?.rules} />
            <MatchedRuleList matches={a.matchedRules} rules={reference.data?.rules} />
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Transaction</h2>
              <Link className="link" to={`/transactions/${a.transactionId}`}>
                Full context →
              </Link>
            </div>
            <dl className="facts">
              <Fact label="Transaction">#{a.transactionId}</Fact>
              <Fact label="Customer">{a.customerName}</Fact>
              <Fact label="Merchant">{a.merchantName}</Fact>
              <Fact label="Amount">{formatAud(a.amount)}</Fact>
              <Fact label="Occurred">{formatDateTime(a.occurredAt)}</Fact>
              <Fact label="Authorisation">
                <OutcomeTag outcome={a.authOutcome} />
              </Fact>
            </dl>
          </section>
        </div>

        <div className="stack">
          <ReviewPanel alert={a} analysts={reference.data?.analysts ?? []} onUpdated={(updated) => {
            setCurrent(updated);
            reviews.reload();
          }} />

          <section className="card">
            <h2>Review history</h2>
            {reviews.error && <ErrorBanner error={reviews.error} onRetry={reviews.reload} />}
            {reviews.data && reviews.data.length === 0 && <p className="muted">No status changes yet.</p>}
            {reviews.data && reviews.data.length > 0 && (
              <ol className="timeline">
                {reviews.data.map((r) => (
                  <li key={r.reviewId}>
                    <div className="timeline-head">
                      <StatusBadge status={r.oldStatus} /> <span aria-label="to">→</span> <StatusBadge status={r.newStatus} />
                    </div>
                    <div className="muted small">
                      {r.changedBy} · {formatDateTime(r.changedAt)}
                    </div>
                    {r.note && <p className="note">{r.note}</p>}
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>
    </>
  );
}

function ReviewPanel({
  alert,
  analysts,
  onUpdated,
}: {
  alert: AlertDetail;
  analysts: { analystId: number; displayName: string }[];
  onUpdated: (alert: AlertDetail) => void;
}) {
  const [analystId, setAnalystId] = useState<string>(() => {
    try {
      return localStorage.getItem(ANALYST_KEY) ?? '';
    } catch {
      return '';
    }
  });
  const [note, setNote] = useState('');
  const [pending, setPending] = useState<NextStatus | null>(null);
  const [error, setError] = useState<Error>();
  const [notice, setNotice] = useState<string>();

  const options = TRANSITIONS[alert.status];

  useEffect(() => {
    if (!analystId && analysts.length > 0) setAnalystId(String(analysts[0].analystId));
  }, [analysts, analystId]);

  const chooseAnalyst = (value: string) => {
    setAnalystId(value);
    try {
      localStorage.setItem(ANALYST_KEY, value);
    } catch {
      // Storage unavailable (private mode); the choice simply isn't remembered.
    }
  };

  const submit = async (newStatus: NextStatus) => {
    setError(undefined);
    setNotice(undefined);
    const terminal = newStatus === 'RESOLVED' || newStatus === 'DISMISSED';
    if (terminal && !note.trim()) {
      setError(new Error(`Add a note explaining why the alert is ${newStatus.toLowerCase()}.`));
      return;
    }
    if (!analystId) {
      setError(new Error('Choose the analyst making this change.'));
      return;
    }
    setPending(newStatus);
    try {
      const updated = await api.updateAlertStatus(alert.alertId, {
        newStatus,
        analystId: Number(analystId),
        note: note.trim() || undefined,
      });
      setNote('');
      setNotice(`Status changed to ${titleCase(newStatus)}.`);
      onUpdated(updated);
    } catch (err) {
      setError(err as Error);
    } finally {
      setPending(null);
    }
  };

  return (
    <section className="card">
      <h2>Review</h2>
      <dl className="facts single">
        <Fact label="Assigned analyst">{alert.assignedAnalyst ?? <span className="muted">Unassigned</span>}</Fact>
        {alert.reviewedAt && <Fact label="Closed">{formatDateTime(alert.reviewedAt)}</Fact>}
        {alert.analystNote && <Fact label="Latest note">{alert.analystNote}</Fact>}
      </dl>

      {options.length === 0 ? (
        <p className="muted">This alert is {alert.status.toLowerCase()}. Closed alerts cannot change status.</p>
      ) : (
        <form className="form" onSubmit={(e) => e.preventDefault()}>
          <label className="field">
            <span>Acting analyst</span>
            <select className="input" value={analystId} onChange={(e) => chooseAnalyst(e.target.value)}>
              {analysts.map((an) => (
                <option key={an.analystId} value={an.analystId}>
                  {an.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>
              Note <span className="muted">(required to resolve or dismiss)</span>
            </span>
            <textarea
              className="input"
              rows={3}
              maxLength={2000}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="What did you check, and what did you conclude?"
            />
          </label>
          {error && <ErrorBanner error={error} />}
          {notice && <div className="banner banner-ok" role="status">{notice}</div>}
          <div className="actions">
            {options.map((next) => (
              <button
                key={next}
                type="button"
                className={`btn ${next === 'DISMISSED' ? 'btn-secondary' : 'btn-primary'}`}
                disabled={pending !== null}
                onClick={() => submit(next)}
              >
                {pending === next ? 'Saving…' : ACTION_LABEL[next]}
              </button>
            ))}
          </div>
        </form>
      )}
    </section>
  );
}

function BackLink() {
  return (
    <Link to="/alerts" className="back link">
      ← Alert queue
    </Link>
  );
}
