import { NavLink, Outlet } from 'react-router-dom';
import { api } from '../api/client';
import { useApi } from '../api/useApi';

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/alerts', label: 'Alert queue' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/simulate', label: 'Simulate transaction' },
  { to: '/demo', label: 'Transaction demo' },
];

export function Layout() {
  const health = useApi(api.dbCheck);
  const connected = health.data?.status === 'connected';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20">
              <path
                d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinejoin="round"
              />
              <path d="m8.5 12 2.5 2.5 4.5-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          <div>
            <div className="brand-name">Fraud Detection</div>
            <div className="brand-sub">Transaction monitoring</div>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="nav-link">
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span className={`conn-dot ${health.loading ? 'pending' : connected ? 'ok' : 'down'}`} aria-hidden="true" />
          <span>
            {health.loading
              ? 'Checking database…'
              : connected
                ? 'PostgreSQL connected'
                : 'Backend unreachable'}
          </span>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
