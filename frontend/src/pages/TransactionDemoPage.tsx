import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatCount } from '../api/format';
import { RiskBadge, RuleChip } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { ErrorBanner, Loading } from '../components/States';
import type {
  CreateTransactionRequest,
  ForcedRollbackResult,
  HeldLockResult,
  RecentAttempts,
  ReferenceData,
  TableCounts,
  TransactionSubmissionResponse,
} from '../types/api';

// The steps TransactionProcessingService.process runs inside one database transaction.
const INTAKE_STEPS = [
  'Lock the card row (SELECT … FOR UPDATE)',
  'Check the request ID has not been used',
  'Insert the PAYMENT_TRANSACTION row',
  'Read earlier history and evaluate the active rules',
  'Insert the RISK_ASSESSMENT snapshot',
  'Insert one RULE_MATCH row per matched rule',
  'Insert a FRAUD_ALERT if the score is 30 or more',
];

const COUNT_ROWS: { key: keyof TableCounts; label: string }[] = [
  { key: 'transactions', label: 'payment_transaction' },
  { key: 'assessments', label: 'risk_assessment' },
  { key: 'ruleMatches', label: 'rule_match' },
  { key: 'alerts', label: 'fraud_alert' },
];

const stamp = () => new Date().toISOString().replace(/\D/g, '').slice(8, 17);

function baseRequest(ref: ReferenceData, cardId: number, requestId: string): CreateTransactionRequest {
  const card = ref.cards.find((c) => c.cardId === cardId);
  return {
    requestId,
    cardId,
    merchantId: ref.merchants[0]?.merchantId ?? 1,
    deviceId: card?.knownDeviceIds[0] ?? ref.devices[0]?.deviceId ?? 1,
    locationId: ref.locations[0]?.locationId ?? 1,
    amount: 12,
    occurredAt: new Date(Date.now() - 1000).toISOString(),
    authOutcome: 'APPROVED',
  };
}

export function TransactionDemoPage() {
  const reference = useApi(api.reference);
  const ref = reference.data;

  return (
    <>
      <PageHeader
        title="Transaction demo"
        subtitle="Shows the boundary of the intake transaction, what happens when it fails part-way, and how simultaneous attempts on one card are handled. Every button runs the real intake code against PostgreSQL."
      />
      {reference.error && <ErrorBanner error={reference.error} onRetry={reference.reload} />}
      {!ref && !reference.error && <Loading />}
      {ref && (
        <div className="stack">
          <BoundaryCard />
          <RollbackCard reference={ref} />
          <ConcurrencyCard reference={ref} />
          <DuplicateCard reference={ref} />
        </div>
      )}
    </>
  );
}

function BoundaryCard() {
  return (
    <section className="card">
      <h2>The intake transaction</h2>
      <p className="card-sub">One submission = one database transaction. Nothing is visible to other users until COMMIT.</p>
      <ol className="steps">
        <li className="step-edge">BEGIN</li>
        {INTAKE_STEPS.map((step, i) => (
          <li key={step}>
            <span className="step-no">{i + 1}</span>
            {step}
          </li>
        ))}
        <li className="step-edge">COMMIT — or ROLLBACK of every step if any step fails</li>
      </ol>
    </section>
  );
}

function CardSelect({ reference, value, onChange }: { reference: ReferenceData; value: number; onChange: (id: number) => void }) {
  return (
    <label className="field inline-field">
      <span>Card</span>
      <select className="input" value={value} onChange={(e) => onChange(Number(e.target.value))}>
        {reference.cards
          .filter((c) => c.cardStatus === 'ACTIVE' && c.accountStatus === 'ACTIVE')
          .map((c) => (
            <option key={c.cardId} value={c.cardId}>
              {c.cardId} · {c.customerName}
            </option>
          ))}
      </select>
    </label>
  );
}

const defaultCard = (ref: ReferenceData, preferred: number) =>
  ref.cards.find((c) => c.cardId === preferred && c.cardStatus === 'ACTIVE')?.cardId ??
  ref.cards.find((c) => c.cardStatus === 'ACTIVE')?.cardId ??
  ref.cards[0]?.cardId ??
  0;

// --- 1. Forced failure and rollback ------------------------------------------------

function RollbackCard({ reference }: { reference: ReferenceData }) {
  const [cardId, setCardId] = useState(() => defaultCard(reference, 1001));
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<ForcedRollbackResult>();
  const [error, setError] = useState<Error>();

  const run = async () => {
    setRunning(true);
    setError(undefined);
    try {
      setResult(await api.forcedRollback(cardId));
    } catch (err) {
      setError(err as Error);
    } finally {
      setRunning(false);
    }
  };

  const unchanged = result && COUNT_ROWS.every(({ key }) => result.before[key] === result.after[key]);

  return (
    <section className="card">
      <h2>1 · Failure part-way through → full rollback</h2>
      <p className="card-sub">
        Submits AUD 7,500 (enough to match R1, so intake reaches step 7) with a fault injected at the alert insert. By then steps 3–6
        have already written rows inside the transaction.
      </p>
      <div className="demo-controls">
        <CardSelect reference={reference} value={cardId} onChange={setCardId} />
        <button type="button" className="btn btn-primary" onClick={run} disabled={running}>
          {running ? 'Running…' : 'Run intake with a forced failure'}
        </button>
      </div>
      {error && <ErrorBanner error={error} />}
      {result && (
        <div className="demo-result">
          <div className="db-error">
            <span className="db-error-label">Database error inside the transaction</span>
            {result.failureMessage}
          </div>
          <table className="table compact counts-table">
            <thead>
              <tr>
                <th>Table</th>
                <th className="num">Rows before</th>
                <th className="num">Rows after</th>
                <th className="num">Change</th>
              </tr>
            </thead>
            <tbody>
              {COUNT_ROWS.map(({ key, label }) => {
                const change = result.after[key] - result.before[key];
                return (
                  <tr key={key}>
                    <td>
                      <code>{label}</code>
                    </td>
                    <td className="num">{formatCount(result.before[key])}</td>
                    <td className="num">{formatCount(result.after[key])}</td>
                    <td className={`num ${change === 0 ? 'ok-text' : 'error-text'}`}>{change === 0 ? '0' : `+${change}`}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <p className={`verdict ${unchanged && !result.requestStored ? 'verdict-ok' : 'verdict-bad'}`}>
            {unchanged && !result.requestStored
              ? `✓ Rolled back: the transaction, assessment and rule matches written in steps 3–6 were all discarded. Request ${result.requestId} was not stored.`
              : `✗ Unexpected: rows changed or request ${result.requestId} was stored.`}
          </p>
        </div>
      )}
    </section>
  );
}

// --- 2. Simultaneous attempts on one card -------------------------------------------

interface Attempt {
  index: number;
  requestId: string;
  result?: HeldLockResult;
  error?: Error;
}

function ConcurrencyCard({ reference }: { reference: ReferenceData }) {
  const [cardId, setCardId] = useState(() => defaultCard(reference, 1002));
  const [count, setCount] = useState(6);
  const [holdMillis, setHoldMillis] = useState(500);
  const [recent, setRecent] = useState<RecentAttempts>();
  const [running, setRunning] = useState(false);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [baseline, setBaseline] = useState<RecentAttempts>();

  useEffect(() => {
    api.recentAttempts(cardId).then(setRecent).catch(() => setRecent(undefined));
  }, [cardId, attempts]);

  const firstExpected = (r: RecentAttempts | undefined) => (r ? Math.max(1, r.minimumEventCount - r.attempts) : undefined);

  const run = async () => {
    setRunning(true);
    setAttempts([]);
    const before = await api.recentAttempts(cardId).catch(() => undefined);
    setBaseline(before);
    const prefix = `demo-burst-${stamp()}`;
    const occurredAt = new Date(Date.now() - 1000).toISOString();
    const requests = Array.from({ length: count }, (_, i) => ({
      ...baseRequest(reference, cardId, `${prefix}-${i + 1}`),
      occurredAt,
    }));
    // Fire every request at once; the server decides the order via the card lock.
    const settled = await Promise.allSettled(requests.map((r) => api.heldLockIntake(r, holdMillis)));
    const rows: Attempt[] = settled.map((s, i) => ({
      index: i + 1,
      requestId: requests[i].requestId,
      result: s.status === 'fulfilled' ? s.value : undefined,
      error: s.status === 'rejected' ? (s.reason as Error) : undefined,
    }));
    rows.sort((a, b) => (a.result?.submission.transactionId ?? Infinity) - (b.result?.submission.transactionId ?? Infinity));
    setAttempts(rows);
    setRunning(false);
  };

  const done = attempts.filter((a) => a.result);
  const maxTotal = Math.max(1, ...done.map((a) => a.result!.totalMillis));
  const expectedFrom = firstExpected(baseline);
  const r3Positions = done.map((a, i) => (a.result!.submission.matchedRules.includes('R3') ? i + 1 : 0)).filter(Boolean);
  const expectedPositions =
    expectedFrom === undefined ? [] : done.map((_, i) => i + 1).filter((p) => p >= expectedFrom);
  const asExpected =
    done.length === attempts.length &&
    r3Positions.length === expectedPositions.length &&
    r3Positions.every((p, i) => p === expectedPositions[i]);

  return (
    <section className="card">
      <h2>2 · Simultaneous attempts on one card</h2>
      <p className="card-sub">
        Sends several attempts at the same moment, like two terminals charging the same card. Under PostgreSQL’s default READ
        COMMITTED isolation, an intake cannot see another intake’s uncommitted row, so without a lock they could all miss the
        velocity rule (R3). Intake therefore locks the card row first: attempts on the same card queue, and each one sees every
        earlier attempt. Attempts on different cards still run in parallel.
      </p>
      <div className="demo-controls">
        <CardSelect reference={reference} value={cardId} onChange={setCardId} />
        <label className="field inline-field">
          <span>Attempts</span>
          <select className="input" value={count} onChange={(e) => setCount(Number(e.target.value))}>
            {[2, 3, 4, 5, 6, 7, 8].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
        <label className="field inline-field">
          <span>Hold the lock per attempt</span>
          <select className="input" value={holdMillis} onChange={(e) => setHoldMillis(Number(e.target.value))}>
            <option value={0}>0 ms (normal speed)</option>
            <option value={250}>250 ms</option>
            <option value={500}>500 ms (queue easy to see)</option>
            <option value={1000}>1,000 ms</option>
          </select>
        </label>
        <button type="button" className="btn btn-primary" onClick={run} disabled={running}>
          {running ? 'Waiting for attempts…' : `Fire ${count} attempts at once`}
        </button>
      </div>
      {(() => {
        const info = attempts.length > 0 ? baseline : recent;
        if (!info) return null;
        return (
          <p className="muted small">
            {attempts.length > 0 ? 'Before this run, card' : 'Card'} {info.cardId} {attempts.length > 0 ? 'had' : 'has'}{' '}
            {info.attempts} attempt{info.attempts === 1 ? '' : 's'} in the last {info.windowSeconds / 60} minutes. R3 needs{' '}
            {info.minimumEventCount}, so attempt #{firstExpected(info)} and later {attempts.length > 0 ? 'should have matched' : 'should match'}.
          </p>
        );
      })()}

      {attempts.length > 0 && (
        <div className="demo-result">
          <table className="table compact">
            <thead>
              <tr>
                <th>Order</th>
                <th>Request ID</th>
                <th className="timeline-col">Time on the server (grey = waiting for the card lock)</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {attempts.map((a, i) => (
                <tr key={a.requestId}>
                  <td>#{i + 1}</td>
                  <td>
                    <code className="small">{a.requestId}</code>
                  </td>
                  <td>
                    {a.result ? (
                      <div className="lane" title={`waited ${a.result.lockWaitMillis} ms, total ${a.result.totalMillis} ms`}>
                        <span className="lane-wait" style={{ width: `${(a.result.lockWaitMillis / maxTotal) * 100}%` }} />
                        <span
                          className="lane-work"
                          style={{ width: `${((a.result.totalMillis - a.result.lockWaitMillis) / maxTotal) * 100}%` }}
                        />
                        <span className="lane-label">waited {formatCount(a.result.lockWaitMillis)} ms</span>
                      </div>
                    ) : (
                      <span className="error-text small">{a.error?.message}</span>
                    )}
                  </td>
                  <td>
                    {a.result && <SubmissionSummary submission={a.result.submission} />}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {done.length > 0 && (
            <p className={`verdict ${asExpected ? 'verdict-ok' : 'verdict-bad'}`}>
              {asExpected
                ? `✓ The attempts ran one at a time in lock order, and R3 matched exactly ${
                    expectedPositions.length === 0
                      ? 'none of them'
                      : `attempt${expectedPositions.length > 1 ? 's' : ''} #${expectedPositions.join(', #')}`
                  }, as the velocity rule predicts${baseline ? ` (${baseline.attempts} earlier attempt${baseline.attempts === 1 ? '' : 's'} in the window)` : ''}.`
                : `R3 matched attempts ${r3Positions.length ? '#' + r3Positions.join(', #') : 'none'}; expected ${
                    expectedPositions.length ? '#' + expectedPositions.join(', #') : 'none'
                  }. Another submission on this card may have landed at the same time — run it again.`}
            </p>
          )}
        </div>
      )}
    </section>
  );
}

function SubmissionSummary({ submission }: { submission: TransactionSubmissionResponse }) {
  return (
    <span className="log-result">
      <Link className="link" to={`/transactions/${submission.transactionId}`}>
        #{submission.transactionId}
      </Link>
      <RiskBadge level={submission.riskLevel} score={submission.score} />
      {submission.matchedRules.map((code) => (
        <RuleChip key={code} code={code} />
      ))}
    </span>
  );
}

// --- 3. Duplicate request --------------------------------------------------------------

function DuplicateCard({ reference }: { reference: ReferenceData }) {
  const [cardId, setCardId] = useState(() => defaultCard(reference, 1001));
  const [running, setRunning] = useState(false);
  const [rows, setRows] = useState<{ label: string; ok?: TransactionSubmissionResponse; error?: Error }[]>([]);

  const run = async () => {
    setRunning(true);
    const body = baseRequest(reference, cardId, `demo-dup-${stamp()}`);
    const out: typeof rows = [];
    for (const label of ['First submission', 'Same request ID again']) {
      try {
        out.push({ label, ok: await api.submitTransaction(body) });
      } catch (err) {
        out.push({ label, error: err as Error });
      }
    }
    setRows(out);
    setRunning(false);
  };

  return (
    <section className="card">
      <h2>3 · Same request sent twice</h2>
      <p className="card-sub">
        A client retry re-sends the same request ID. The UNIQUE request_id and the idempotency check in step 2 stop it creating a
        second transaction.
      </p>
      <div className="demo-controls">
        <CardSelect reference={reference} value={cardId} onChange={setCardId} />
        <button type="button" className="btn btn-primary" onClick={run} disabled={running}>
          {running ? 'Sending…' : 'Send the same request twice'}
        </button>
      </div>
      {rows.length > 0 && (
        <ul className="dup-list">
          {rows.map((r) => (
            <li key={r.label}>
              <strong>{r.label}:</strong>{' '}
              {r.ok ? (
                <SubmissionSummary submission={r.ok} />
              ) : (
                <span className="error-text">
                  {r.error instanceof ApiError && r.error.status ? `${r.error.status} · ` : ''}
                  {r.error?.message}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
