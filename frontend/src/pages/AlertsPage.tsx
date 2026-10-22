import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatDateTime } from '../api/format';
import { RiskBadge, StatusBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBanner, Loading } from '../components/States';
import type { AlertStatus, RiskLevel } from '../types/api';
import { byNewest, byPriority } from './alertSort';

const STATUS_TABS: { value: AlertStatus | 'ACTIVE' | 'ALL'; label: string }[] = [
  { value: 'ACTIVE', label: 'Needs review' },
  { value: 'OPEN', label: 'Open' },
  { value: 'INVESTIGATING', label: 'Investigating' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'DISMISSED', label: 'Dismissed' },
  { value: 'ALL', label: 'All' },
];

export function AlertsPage() {
  const { data, error, loading, reload } = useApi(api.alerts);
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? 'ACTIVE') as (typeof STATUS_TABS)[number]['value'];
  const [risk, setRisk] = useState<RiskLevel | ''>('');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<'priority' | 'newest'>('priority');

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: 0, ACTIVE: 0, OPEN: 0, INVESTIGATING: 0, RESOLVED: 0, DISMISSED: 0 };
    for (const a of data ?? []) {
      c.ALL++;
      c[a.status]++;
      if (a.status === 'OPEN' || a.status === 'INVESTIGATING') c.ACTIVE++;
    }
    return c;
  }, [data]);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (data ?? [])
      .filter((a) =>
        status === 'ALL'
          ? true
          : status === 'ACTIVE'
            ? a.status === 'OPEN' || a.status === 'INVESTIGATING'
            : a.status === status,
      )
      .filter((a) => !risk || a.riskLevel === risk)
      .filter(
        (a) =>
          !q ||
          a.customerName.toLowerCase().includes(q) ||
          a.merchantName.toLowerCase().includes(q) ||
          String(a.alertId).includes(q) ||
          String(a.transactionId).includes(q),
      )
      .sort(sort === 'priority' ? byPriority : byNewest);
  }, [data, status, risk, query, sort]);

  return (
    <>
      <PageHeader
        title="Alert queue"
        subtitle="Alerts raised for assessments scoring 30 or more. Prioritised by risk, then oldest first."
        actions={
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Refresh
          </button>
        }
      />

      <div className="tabs" role="tablist" aria-label="Alert status">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={status === tab.value}
            className={`tab ${status === tab.value ? 'active' : ''}`}
            onClick={() => setParams(tab.value === 'ACTIVE' ? {} : { status: tab.value })}
          >
            {tab.label}
            <span className="tab-count">{counts[tab.value]}</span>
          </button>
        ))}
      </div>

      <div className="filters">
        <input
          type="search"
          className="input"
          placeholder="Search customer, merchant, alert or transaction ID"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select className="input" value={risk} onChange={(e) => setRisk(e.target.value as RiskLevel | '')} aria-label="Risk level">
          <option value="">Any risk</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
        <select className="input" value={sort} onChange={(e) => setSort(e.target.value as 'priority' | 'newest')} aria-label="Sort order">
          <option value="priority">Sort: priority</option>
          <option value="newest">Sort: newest</option>
        </select>
      </div>

      {error && <ErrorBanner error={error} onRetry={reload} />}
      {loading && !data && <Loading />}

      {data && (
        <div className="card flush">
          {rows.length === 0 ? (
            <Empty>No alerts match these filters.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table clickable">
                <thead>
                  <tr>
                    <th>Alert</th>
                    <th>Risk</th>
                    <th>Status</th>
                    <th>Customer</th>
                    <th>Merchant</th>
                    <th className="num">Amount</th>
                    <th>Analyst</th>
                    <th>Raised</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((a) => (
                    <tr key={a.alertId} onClick={() => navigate(`/alerts/${a.alertId}`)}>
                      <td>
                        <Link className="link" to={`/alerts/${a.alertId}`} onClick={(e) => e.stopPropagation()}>
                          #{a.alertId}
                        </Link>
                      </td>
                      <td>
                        <RiskBadge level={a.riskLevel} score={a.score} />
                      </td>
                      <td>
                        <StatusBadge status={a.status} />
                      </td>
                      <td>{a.customerName}</td>
                      <td>{a.merchantName}</td>
                      <td className="num">{formatAud(a.amount)}</td>
                      <td>{a.assignedAnalyst ?? <span className="muted">Unassigned</span>}</td>
                      <td className="nowrap">{formatDateTime(a.createdAt)}</td>
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
