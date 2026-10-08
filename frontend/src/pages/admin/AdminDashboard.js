import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api';
import { useLang } from '../../context/LangContext';
import { useAuth } from '../../context/AuthContext';
import StatusBadge from '../../components/StatusBadge';
import { formatDate, formatPrice, localized } from '../../utils/format';

function Stat({ label, value, hint }) {
  return (
    <div className="stat card">
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint && <span className="small muted">{hint}</span>}
    </div>
  );
}

function RevenueChart({ data, lang }) {
  const max = Math.max(1, ...data.map((d) => Number(d.revenue)));
  return (
    <div className="bar-chart" role="img" aria-label="revenue chart">
      {data.map((d) => {
        const h = (Number(d.revenue) / max) * 100;
        const day = new Date(d.date + 'T00:00:00');
        return (
          <div key={d.date} className="bar-col" title={`${formatDate(day, lang)} · ${formatPrice(d.revenue, lang)} · ${d.orders}`}>
            <div className="bar-track">
              <div className="bar" style={{ height: `${Math.max(h, d.revenue > 0 ? 3 : 0)}%` }} />
            </div>
            <span className="bar-label">{day.getDate()}</span>
          </div>
        );
      })}
    </div>
  );
}

function HBar({ rows }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <ul className="hbar">
      {rows.map((r) => (
        <li key={r.key}>
          <span className="hbar-label">{r.label}</span>
          <span className="hbar-track"><span className={`hbar-fill ${r.cls || ''}`} style={{ width: `${(r.value / max) * 100}%` }} /></span>
          <span className="hbar-value">{r.value}</span>
        </li>
      ))}
    </ul>
  );
}

export default function AdminDashboard() {
  const { t, lang } = useLang();
  const { isAdmin } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    api.get('/admin/dashboard').then((r) => setData(r.data)).catch(() => setError(true));
  }, []);

  if (error) return <div className="admin-page"><div className="alert alert-error">{t.common.error}</div></div>;
  if (!data) return <div className="admin-page muted">{t.common.loading}</div>;

  const statusRows = Object.entries(data.ordersByStatus).map(([k, v]) => ({
    key: k, label: t.status[k], value: v, cls: `fill-${k.toLowerCase()}`,
  }));
  const paymentRows = Object.entries(data.ordersByPaymentMethod).map(([k, v]) => ({
    key: k, label: t.paymentMethod[k], value: v,
  }));

  return (
    <div className="admin-page">
      <h1>{t.admin.dashboard}</h1>

      <div className="stat-grid">
        <Stat label={t.admin.revenue} value={formatPrice(data.totalRevenue, lang)} hint={`${t.admin.paidRevenue}: ${formatPrice(data.paidRevenue, lang)}`} />
        <Stat label={t.admin.totalOrders} value={data.totalOrders} hint={`${t.admin.pendingOrders}: ${data.pendingOrders}`} />
        <Stat label={t.admin.totalCustomers} value={data.totalCustomers} />
        <Stat label={t.admin.totalProducts} value={data.totalProducts} />
      </div>

      <div className="admin-grid">
        <section className="card span-2">
          <h3>{t.admin.last14Days}</h3>
          <RevenueChart data={data.dailyStats} lang={lang} />
        </section>
        <section className="card">
          <h3>{t.admin.ordersByStatus}</h3>
          <HBar rows={statusRows} />
        </section>
        <section className="card">
          <h3>{t.admin.paymentMix}</h3>
          <HBar rows={paymentRows} />
        </section>
        <section className="card">
          <h3>{t.admin.topProducts}</h3>
          {data.topProducts.length === 0 ? (
            <p className="muted">{t.common.noResults}</p>
          ) : (
            <ol className="rank-list">
              {data.topProducts.map((p) => (
                <li key={p.id}>
                  <span>{localized(p, 'name', lang)}</span>
                  <span className="muted small">{p.unitsSold} {t.admin.unitsSold}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
        <section className="card">
          <h3>{t.admin.lowStock}</h3>
          {data.lowStockProducts.length === 0 ? (
            <p className="muted">{t.common.noResults}</p>
          ) : (
            <ul className="rank-list">
              {data.lowStockProducts.map((p) => (
                <li key={p.id}>
                  <span>{localized(p, 'name', lang)}</span>
                  <span className={`pill ${p.stockQuantity === 0 ? 'pill-bad' : 'pill-warn'}`}>{p.stockQuantity}</span>
                </li>
              ))}
            </ul>
          )}
          {isAdmin && <Link to="/admin/products" className="link small">{t.admin.products} →</Link>}
        </section>
        <section className="card span-2">
          <div className="section-head">
            <h3>{t.admin.recentOrders}</h3>
            <Link to="/admin/orders" className="link small">{t.common.viewAll}</Link>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{t.admin.customer}</th>
                  <th>{t.common.date}</th>
                  <th>{t.common.status}</th>
                  <th className="num">{t.common.total}</th>
                </tr>
              </thead>
              <tbody>
                {data.recentOrders.map((o) => (
                  <tr key={o.id}>
                    <td>{o.id}</td>
                    <td>{o.customerDetails?.fullName || o.user?.fullName || t.admin.guest}</td>
                    <td>{formatDate(o.createdAt, lang, true)}</td>
                    <td><StatusBadge status={o.status} /></td>
                    <td className="num">{formatPrice(o.totalAmount, lang)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}
