import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatDateTime } from '../api/format';
import { OutcomeTag, RiskBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBanner, Loading } from '../components/States';
import type { AuthOutcome, RiskLevel } from '../types/api';

export function TransactionsPage() {
  const { data, error, loading, reload } = useApi(api.transactions);
  const reference = useApi(api.reference);
  const navigate = useNavigate();

  const [risk, setRisk] = useState<RiskLevel | ''>('');
  const [outcome, setOutcome] = useState<AuthOutcome | ''>('');
  const [cardId, setCardId] = useState('');
  const [query, setQuery] = useState('');

  const customerByCard = useMemo(
    () => new Map(reference.data?.cards.map((c) => [c.cardId, c.customerName]) ?? []),
    [reference.data],
  );

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (data ?? [])
      .filter((t) => !risk || t.riskLevel === risk)
      .filter((t) => !outcome || t.authOutcome === outcome)
      .filter((t) => !cardId || t.cardId === Number(cardId))
      .filter(
        (t) =>
          !q ||
          t.merchantName.toLowerCase().includes(q) ||
          String(t.transactionId).includes(q) ||
          (customerByCard.get(t.cardId) ?? '').toLowerCase().includes(q),
      );
  }, [data, risk, outcome, cardId, query, customerByCard]);

  return (
    <>
      <PageHeader
        title="Transactions"
        subtitle="The 100 most recent transactions with their stored risk assessment."
        actions={
          <>
            <button type="button" className="btn btn-ghost" onClick={reload}>
              Refresh
            </button>
            <Link to="/simulate" className="btn btn-primary">
              Simulate transaction
            </Link>
          </>
        }
      />

      <div className="filters">
        <input
          type="search"
          className="input"
          placeholder="Search merchant, customer or transaction ID"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select className="input" value={cardId} onChange={(e) => setCardId(e.target.value)} aria-label="Card">
          <option value="">Any card</option>
          {reference.data?.cards.map((c) => (
            <option key={c.cardId} value={c.cardId}>
              {c.cardId} · {c.customerName}
            </option>
          ))}
        </select>
        <select className="input" value={risk} onChange={(e) => setRisk(e.target.value as RiskLevel | '')} aria-label="Risk level">
          <option value="">Any risk</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
        <select className="input" value={outcome} onChange={(e) => setOutcome(e.target.value as AuthOutcome | '')} aria-label="Authorisation outcome">
          <option value="">Any outcome</option>
          <option value="APPROVED">Approved</option>
          <option value="DECLINED">Declined</option>
        </select>
      </div>

      {error && <ErrorBanner error={error} onRetry={reload} />}
      {loading && !data && <Loading />}

      {data && (
        <div className="card flush">
          {rows.length === 0 ? (
            <Empty>No transactions match these filters.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table clickable">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Occurred</th>
                    <th>Customer · card</th>
                    <th>Merchant</th>
                    <th className="num">Amount</th>
                    <th>Outcome</th>
                    <th>Risk</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((t) => (
                    <tr key={t.transactionId} onClick={() => navigate(`/transactions/${t.transactionId}`)}>
                      <td>
                        <Link className="link" to={`/transactions/${t.transactionId}`} onClick={(e) => e.stopPropagation()}>
                          #{t.transactionId}
                        </Link>
                      </td>
                      <td className="nowrap">{formatDateTime(t.occurredAt)}</td>
                      <td>
                        {customerByCard.get(t.cardId) ?? '—'} <span className="muted">· {t.cardId}</span>
                      </td>
                      <td>{t.merchantName}</td>
                      <td className="num">{formatAud(t.amount)}</td>
                      <td>
                        <OutcomeTag outcome={t.authOutcome} />
                      </td>
                      <td>
                        <RiskBadge level={t.riskLevel} score={t.score} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}
