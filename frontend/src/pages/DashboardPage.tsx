import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatCount, formatDateTime, titleCase } from '../api/format';
import { BarList } from '../components/BarList';
import { RiskBadge, StatusBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBanner, Loading } from '../components/States';
import type { AlertStatus, RiskLevel } from '../types/api';
import { byPriority } from './alertSort';

const RISK_ORDER: RiskLevel[] = ['HIGH', 'MEDIUM', 'LOW'];
const STATUS_ORDER: AlertStatus[] = ['OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED'];

export function DashboardPage() {
  const dashboard = useApi(api.dashboard);
  const alerts = useApi(api.alerts);

  const reload = () => {
    dashboard.reload();
    alerts.reload();
  };

  const d = dashboard.data;
  const needsReview = (alerts.data ?? [])
    .filter((a) => a.status === 'OPEN' || a.status === 'INVESTIGATING')
    .sort(byPriority)
    .slice(0, 5);

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Live counts from PostgreSQL. Every transaction is assessed; scores of 30 or more raise an alert."
        actions={
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Refresh
          </button>
        }
      />

      {dashboard.error && <ErrorBanner error={dashboard.error} onRetry={reload} />}
      {dashboard.loading && !d && <Loading />}

      {d && (
        <>
          <section className="kpis">
            <Kpi label="Transactions screened" value={formatCount(d.transactionCount)} note={`${formatAud(d.totalAmount)} total`} />
            <Kpi
              label="Alerts awaiting review"
              value={formatCount(d.alertStatusCounts.OPEN + d.alertStatusCounts.INVESTIGATING)}
              note={`${d.alertStatusCounts.OPEN} open · ${d.alertStatusCounts.INVESTIGATING} investigating`}
            />
            <Kpi
              label="High-risk assessments"
              value={formatCount(d.riskLevelCounts.HIGH)}
              note={`${d.riskLevelCounts.MEDIUM} medium · ${d.riskLevelCounts.LOW} low`}
            />
            <Kpi
              label="Declined attempts"
              value={formatCount(d.declinedCount)}
              note={`${d.transactionCount ? Math.round((d.declinedCount / d.transactionCount) * 100) : 0}% of transactions`}
            />
          </section>

          <section className="grid-2">
            <div className="card">
              <h2>Assessments by risk level</h2>
              <BarList
                caption="Assessments by risk level"
                items={RISK_ORDER.map((level) => ({
                  key: level,
                  label: <RiskBadge level={level} />,
                  value: d.riskLevelCounts[level] ?? 0,
                  tone: `risk-${level.toLowerCase()}`,
                }))}
              />
            </div>
            <div className="card">
              <h2>Alerts by review status</h2>
              <BarList
                caption="Alerts by review status"
                items={STATUS_ORDER.map((status) => ({
                  key: status,
                  label: <StatusBadge status={status} />,
                  value: d.alertStatusCounts[status] ?? 0,
                }))}
              />
            </div>
          </section>

          <section className="grid-2">
            <div className="card">
              <h2>Rule hits</h2>
              <p className="card-sub">Matches per active rule (RuleMatch grouped by rule)</p>
              <BarList
                caption="Rule hits"
                items={d.rulePerformance.map((rule) => ({
                  key: rule.ruleCode,
                  label: (
                    <span className="rule-label">
                      <strong>{rule.ruleCode}</strong> {rule.title}
                    </span>
                  ),
                  value: rule.matchCount,
                  detail: `${rule.matchCount} matches · ${rule.linkedAlertCount} linked alerts · weight ${rule.severityWeight}`,
                }))}
              />
            </div>
            <div className="card">
              <h2>Analyst workload</h2>
              <p className="card-sub">Alerts assigned to each active analyst</p>
              <table className="table compact">
                <thead>
                  <tr>
                    <th>Analyst</th>
                    <th className="num">Active</th>
                    <th className="num">Resolved</th>
                    <th className="num">Dismissed</th>
                  </tr>
                </thead>
                <tbody>
                  {d.analystWorkload.map((a) => (
                    <tr key={a.analystId}>
                      <td>{a.displayName}</td>
                      <td className="num">{a.activeAlerts}</td>
                      <td className="num">{a.resolvedAlerts}</td>
                      <td className="num">{a.dismissedAlerts}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <h2 className="mt">High-risk merchants</h2>
              {d.highRiskMerchants.length === 0 ? (
                <Empty>No high-risk transactions yet.</Empty>
              ) : (
                <table className="table compact">
                  <thead>
                    <tr>
                      <th>Merchant</th>
                      <th className="num">High-risk</th>
                      <th className="num">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {d.highRiskMerchants.map((m) => (
                      <tr key={m.merchantId}>
                        <td>
                          {m.merchantName} <span className="muted">· {titleCase(m.category)}</span>
                        </td>
                        <td className="num">{m.highRiskCount}</td>
                        <td className="num">{formatAud(m.highRiskAmount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Needs review</h2>
              <Link to="/alerts" className="link">
                Open alert queue →
              </Link>
            </div>
            {alerts.error && <ErrorBanner error={alerts.error} />}
            {needsReview.length === 0 && !alerts.loading ? (
              <Empty>No open or investigating alerts.</Empty>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Alert</th>
                    <th>Risk</th>
                    <th>Customer</th>
                    <th>Merchant</th>
                    <th className="num">Amount</th>
                    <th>Status</th>
                    <th>Raised</th>
                  </tr>
                </thead>
                <tbody>
                  {needsReview.map((a) => (
                    <tr key={a.alertId}>
                      <td>
                        <Link className="link" to={`/alerts/${a.alertId}`}>
                          #{a.alertId}
                        </Link>
                      </td>
                      <td>
                        <RiskBadge level={a.riskLevel} score={a.score} />
                      </td>
                      <td>{a.customerName}</td>
                      <td>{a.merchantName}</td>
                      <td className="num">{formatAud(a.amount)}</td>
                      <td>
                        <StatusBadge status={a.status} />
                      </td>
                      <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </>
  );
}

function Kpi({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {note && <div className="kpi-note">{note}</div>}
    </div>
  );
}
