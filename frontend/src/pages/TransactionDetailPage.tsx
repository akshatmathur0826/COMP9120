import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';
import { formatAud, formatDateTime, titleCase } from '../api/format';
import { OutcomeTag, StatusBadge } from '../components/Badges';
import { Fact } from '../components/Fact';
import { PageHeader } from '../components/PageHeader';
import { MatchedRuleList, ScoreBreakdown } from '../components/ScoreBreakdown';
import { ErrorBanner, Loading } from '../components/States';

export function TransactionDetailPage() {
  const { id = '' } = useParams();
  const { data: t, error, reload } = useApi(() => api.transaction(id), [id]);
  const reference = useApi(api.reference);

  return (
    <>
      <Link to="/transactions" className="back link">
        ← Transactions
      </Link>
      {error && <ErrorBanner error={error} onRetry={reload} />}
      {!t && !error && <Loading />}
      {t && (
        <>
          <PageHeader
            title={`Transaction #${t.transactionId}`}
            subtitle={`${formatAud(t.amount)} at ${t.merchantName} · ${formatDateTime(t.occurredAt)}`}
          />
          <div className="detail-grid">
            <section className="card">
              <h2>Context</h2>
              <dl className="facts">
                <Fact label="Customer">{t.customerName}</Fact>
                <Fact label="Account">{t.accountNumber}</Fact>
                <Fact label="Card">
                  {t.cardId} <span className="muted">· {t.cardToken}</span>
                </Fact>
                <Fact label="Merchant">
                  {t.merchantName} <span className="muted">· {titleCase(t.merchantCategory)}</span>
                </Fact>
                <Fact label="Device">{t.deviceFingerprint}</Fact>
                <Fact label="Location">
                  {t.city}, {t.countryCode}
                </Fact>
                <Fact label="Amount">{formatAud(t.amount)}</Fact>
                <Fact label="Occurred">{formatDateTime(t.occurredAt)}</Fact>
                <Fact label="Authorisation">
                  <OutcomeTag outcome={t.authOutcome} />
                </Fact>
                <Fact label="Request ID">
                  <code>{t.requestId}</code>
                </Fact>
              </dl>
            </section>

            <section className="card">
              <div className="card-head">
                <h2>Risk assessment</h2>
                {t.alertId && (
                  <Link className="link" to={`/alerts/${t.alertId}`}>
                    Alert #{t.alertId} →
                  </Link>
                )}
              </div>
              {t.score == null || !t.riskLevel ? (
                <p className="muted">No assessment is recorded for this transaction.</p>
              ) : (
                <>
                  <ScoreBreakdown score={t.score} riskLevel={t.riskLevel} matches={t.matchedRules} rules={reference.data?.rules} />
                  <dl className="facts single">
                    <Fact label="Rule set">{t.ruleSetVersion}</Fact>
                    <Fact label="Alert">
                      {t.alertId ? <StatusBadge status={t.alertStatus} /> : <span className="muted">None (score below 30)</span>}
                    </Fact>
                  </dl>
                  <MatchedRuleList matches={t.matchedRules} rules={reference.data?.rules} />
                </>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
