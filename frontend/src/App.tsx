import { Link, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Empty } from './components/States';
import { AlertDetailPage } from './pages/AlertDetailPage';
import { AlertsPage } from './pages/AlertsPage';
import { DashboardPage } from './pages/DashboardPage';
import { SimulatePage } from './pages/SimulatePage';
import { TransactionDemoPage } from './pages/TransactionDemoPage';
import { TransactionDetailPage } from './pages/TransactionDetailPage';
import { TransactionsPage } from './pages/TransactionsPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="alerts/:id" element={<AlertDetailPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="transactions/:id" element={<TransactionDetailPage />} />
        <Route path="simulate" element={<SimulatePage />} />
        <Route path="demo" element={<TransactionDemoPage />} />
        <Route
          path="*"
          element={
            <Empty>
              Page not found. <Link className="link" to="/">Back to the dashboard</Link>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
