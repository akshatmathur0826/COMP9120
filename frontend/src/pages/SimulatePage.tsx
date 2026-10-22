import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatDateTime, titleCase } from '../api/format';
import { RiskBadge, RuleChip } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { MatchedRuleList, ScoreBreakdown } from '../components/ScoreBreakdown';
import { ErrorBanner, Loading } from '../components/States';
import type {
  CreateTransactionRequest,
  ReferenceData,
  TransactionDetail,
  TransactionSubmissionResponse,
} from '../types/api';
import { SCENARIOS, type FormState } from './scenarios';

interface Attempt {
  key: string;
  request: CreateTransactionRequest;
  response?: TransactionSubmissionResponse;
  error?: ApiError | Error;
}

const newRequestId = () =>
  `ui-${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-${Math.random().toString(36).slice(2, 7)}`;

const EMPTY_FORM: FormState = {
  cardId: '',
  merchantId: '',
  deviceId: '',
  locationId: '',
  amount: '',
  authOutcome: 'APPROVED',
  repeat: 1,
};

export function SimulatePage() {
  const reference = useApi(api.reference);
  const ref = reference.data;

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [scenarioKey, setScenarioKey] = useState<string>();
  const [requestId, setRequestId] = useState(newRequestId);
  const [useNow, setUseNow] = useState(true);
  const [customTime, setCustomTime] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string>();
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [detail, setDetail] = useState<TransactionDetail>();
  const [detailLoading, setDetailLoading] = useState(false);

  // Initialise the form once reference data arrives.
  useEffect(() => {
    if (ref && !form.cardId) setForm(SCENARIOS[0].build(ref));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ref]);

  const card = ref?.cards.find((c) => String(c.cardId) === form.cardId);
  const scenario = SCENARIOS.find((s) => s.key === scenarioKey);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setScenarioKey(undefined);
  };

  const applyScenario = (key: string) => {
    if (!ref) return;
    const s = SCENARIOS.find((x) => x.key === key)!;
    setForm(s.build(ref));
    setScenarioKey(key);
    setRequestId(newRequestId());
    setUseNow(true);
    setFormError(undefined);
  };

  const validate = (): string | undefined => {
    if (!form.cardId || !form.merchantId || !form.deviceId || !form.locationId) {
      return 'Choose a card, merchant, device and location.';
    }
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(form.amount.trim()) || Number(form.amount) <= 0) {
      return 'Amount must be greater than zero with at most two decimal places.';
    }
    if (!requestId.trim()) return 'A request ID is required.';
    if (!useNow) {
      if (!customTime) return 'Choose when the transaction occurred, or use the current time.';
      if (new Date(customTime).getTime() > Date.now()) return 'The occurred time cannot be in the future.';
    }
    return undefined;
  };

  const buildRequest = (id: string): CreateTransactionRequest => ({
    requestId: id,
    cardId: Number(form.cardId),
    merchantId: Number(form.merchantId),
    deviceId: Number(form.deviceId),
    locationId: Number(form.locationId),
    amount: Number(form.amount),
    occurredAt: useNow ? new Date().toISOString() : new Date(customTime).toISOString(),
    authOutcome: form.authOutcome,
  });

  const run = async (requests: CreateTransactionRequest[]) => {
    setSubmitting(true);
    setFormError(undefined);
    let lastSuccess: number | undefined;
    // Sequential on purpose: each attempt must commit before the next one is
    // evaluated, so velocity and decline rules see the earlier attempts.
    for (const request of requests) {
      const attempt: Attempt = { key: `${request.requestId}-${Date.now()}-${Math.random()}`, request };
      try {
        attempt.response = await api.submitTransaction({ ...request, occurredAt: useNow ? new Date().toISOString() : request.occurredAt });
        lastSuccess = attempt.response.transactionId;
      } catch (err) {
        attempt.error = err as Error;
      }
      setAttempts((prev) => [attempt, ...prev].slice(0, 30));
    }
    setSubmitting(false);
    setRequestId(newRequestId());
    reference.reload(); // card/device history changed, so "new device" hints update

    if (lastSuccess) {
      setDetailLoading(true);
      try {
        setDetail(await api.transaction(lastSuccess));
      } catch {
        setDetail(undefined);
      } finally {
        setDetailLoading(false);
      }
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const problem = validate();
    if (problem) {
      setFormError(problem);
      return;
    }
    const count = Math.max(1, Math.min(10, form.repeat));
    const ids = count === 1 ? [requestId.trim()] : Array.from({ length: count }, (_, i) => `${requestId.trim()}-${i + 1}`);
    run(ids.map(buildRequest));
  };

  const lastRequest = attempts.find((a) => a.response)?.request;
  const resubmitDuplicate = () => {
    if (lastRequest) run([{ ...lastRequest }]);
  };

  return (
    <>
      <PageHeader
        title="Simulate a transaction"
        subtitle="Submits a card transaction to POST /api/transactions. The backend stores it, evaluates the active rules against card history, and records the assessment, rule matches and any alert in one database transaction."
      />

      {reference.error && <ErrorBanner error={reference.error} onRetry={reference.reload} />}
      {!ref && !reference.error && <Loading label="Loading cards, merchants and devices…" />}

      {ref && (
        <div className="detail-grid sim-grid">
          <section className="card">
            <h2>Scenario</h2>
            <div className="presets">
              {SCENARIOS.map((s) => (
                <button
                  key={s.key}
                  type="button"
                  className={`preset ${scenarioKey === s.key ? 'active' : ''}`}
                  onClick={() => applyScenario(s.key)}
                >
                  {s.label}
                </button>
              ))}
            </div>
            <p className="muted small preset-aim">
              {scenario ? scenario.aim : 'Pick a preset, or fill in the form yourself.'}
            </p>

            <form className="form" onSubmit={submit} noValidate>
              <div className="form-grid">
                <label className="field">
                  <span>Card</span>
                  <select className="input" value={form.cardId} onChange={(e) => update('cardId', e.target.value)}>
                    {ref.cards.map((c) => (
                      <option key={c.cardId} value={c.cardId}>
                        {c.cardId} · {c.customerName}
                        {c.cardStatus !== 'ACTIVE' || c.accountStatus !== 'ACTIVE' ? ` (${titleCase(c.cardStatus)})` : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Merchant</span>
                  <select className="input" value={form.merchantId} onChange={(e) => update('merchantId', e.target.value)}>
                    {ref.merchants.map((m) => (
                      <option key={m.merchantId} value={m.merchantId}>
                        {m.merchantName} · {titleCase(m.category)}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Device</span>
                  <select className="input" value={form.deviceId} onChange={(e) => update('deviceId', e.target.value)}>
                    {ref.devices.map((d) => (
                      <option key={d.deviceId} value={d.deviceId}>
                        {d.deviceFingerprint}
                        {card ? (card.knownDeviceIds.includes(d.deviceId) ? ' · used before' : ' · new for this card') : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Location</span>
                  <select className="input" value={form.locationId} onChange={(e) => update('locationId', e.target.value)}>
                    {ref.locations.map((l) => (
                      <option key={l.locationId} value={l.locationId}>
                        {l.city}, {l.countryCode}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Amount (AUD)</span>
                  <input
                    className="input num-input"
                    inputMode="decimal"
                    value={form.amount}
                    onChange={(e) => update('amount', e.target.value)}
                    placeholder="0.00"
                  />
                </label>
                <div className="field">
                  <span>Authorisation outcome</span>
                  <div className="segmented" role="radiogroup" aria-label="Authorisation outcome">
                    {(['APPROVED', 'DECLINED'] as const).map((o) => (
                      <button
                        key={o}
                        type="button"
                        role="radio"
                        aria-checked={form.authOutcome === o}
                        className={form.authOutcome === o ? 'on' : ''}
                        onClick={() => update('authOutcome', o)}
                      >
                        {titleCase(o)}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="field">
                  <span>Occurred at</span>
                  <label className="check">
                    <input type="checkbox" checked={useNow} onChange={(e) => setUseNow(e.target.checked)} />
                    Use the current time
                  </label>
                  {!useNow && (
                    <input
                      type="datetime-local"
                      className="input"
                      value={customTime}
                      onChange={(e) => setCustomTime(e.target.value)}
                    />
                  )}
                </div>
                <label className="field">
                  <span>Attempts to submit</span>
                  <input
                    type="number"
                    className="input num-input"
                    min={1}
                    max={10}
                    value={form.repeat}
                    onChange={(e) => update('repeat', Number(e.target.value) || 1)}
                  />
                </label>
              </div>

              <label className="field">
                <span>
                  Request ID <span className="muted">(idempotency key{form.repeat > 1 ? '; suffixed -1, -2… per attempt' : ''})</span>
                </span>
                <div className="input-row">
                  <input className="input mono" value={requestId} maxLength={76} onChange={(e) => setRequestId(e.target.value)} />
                  <button type="button" className="btn btn-ghost" onClick={() => setRequestId(newRequestId())}>
                    New ID
                  </button>
                </div>
              </label>

              {!useNow && (
                <p className="muted small">
                  Backdated events are scored against the history that exists now. Earlier assessments are not re-evaluated.
                </p>
              )}
              {formError && <div className="banner banner-error" role="alert">{formError}</div>}

              <div className="actions">
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Submitting…' : form.repeat > 1 ? `Submit ${form.repeat} attempts` : 'Submit transaction'}
                </button>
                {lastRequest && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={submitting}
                    onClick={resubmitDuplicate}
                    title={`Re-sends request ID ${lastRequest.requestId}`}
                  >
                    Resend last request ID
                  </button>
                )}
              </div>
            </form>
          </section>

          <div className="stack">
            <section className="card">
              <h2>Latest assessment</h2>
              {detailLoading && <Loading />}
              {!detailLoading && !detail && <p className="muted">Submit a transaction to see its decision and rule explanations here.</p>}
              {!detailLoading && detail && <LatestResult detail={detail} rules={ref.rules} />}
            </section>

            <section className="card">
              <div className="card-head">
                <h2>Submission log</h2>
                {attempts.length > 0 && (
                  <button type="button" className="btn btn-ghost small" onClick={() => setAttempts([])}>
                    Clear
                  </button>
                )}
              </div>
              {attempts.length === 0 ? (
                <p className="muted">Nothing submitted in this session yet.</p>
              ) : (
                <ol className="log">
                  {attempts.map((a) => (
                    <li key={a.key} className={a.error ? 'log-error' : ''}>
                      <div className="log-main">
                        <code className="small">{a.request.requestId}</code>
                        <span className="muted small">{formatAud(a.request.amount)}</span>
                      </div>
                      {a.response ? (
                        <div className="log-result">
                          <Link className="link" to={`/transactions/${a.response.transactionId}`}>
                            #{a.response.transactionId}
                          </Link>
                          <RiskBadge level={a.response.riskLevel} score={a.response.score} />
                          {a.response.matchedRules.map((code) => (
                            <RuleChip key={code} code={code} />
                          ))}
                          {a.response.alertId ? (
                            <Link className="link" to={`/alerts/${a.response.alertId}`}>
                              Alert #{a.response.alertId}
                            </Link>
                          ) : (
                            <span className="muted small">No alert</span>
                          )}
                        </div>
                      ) : (
                        <div className="log-result error-text">
                          {a.error instanceof ApiError && a.error.status ? `${a.error.status} · ` : ''}
                          {a.error?.message}
                          {a.error instanceof ApiError &&
                            Object.entries(a.error.fieldErrors).map(([f, m]) => (
                              <span key={f} className="small">
                                {' '}
                                · {f}: {m}
                              </span>
                            ))}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </div>
      )}
    </>
  );
}

function LatestResult({ detail, rules }: { detail: TransactionDetail; rules: ReferenceData['rules'] }) {
  const summary = useMemo(
    () => `${formatAud(detail.amount)} · ${detail.customerName} at ${detail.merchantName} · ${formatDateTime(detail.occurredAt)}`,
    [detail],
  );
  return (
    <div className="stack tight">
      <p className="small">
        <Link className="link" to={`/transactions/${detail.transactionId}`}>
          Transaction #{detail.transactionId}
        </Link>{' '}
        <span className="muted">· {summary}</span>
      </p>
      {detail.score != null && detail.riskLevel && (
        <ScoreBreakdown score={detail.score} riskLevel={detail.riskLevel} matches={detail.matchedRules} rules={rules} />
      )}
      <MatchedRuleList matches={detail.matchedRules} rules={rules} />
      {detail.alertId ? (
        <Link className="btn btn-primary" to={`/alerts/${detail.alertId}`}>
          Review alert #{detail.alertId}
        </Link>
      ) : (
        <p className="muted small">Score below 30, so no alert was raised.</p>
      )}
    </div>
  );
}
