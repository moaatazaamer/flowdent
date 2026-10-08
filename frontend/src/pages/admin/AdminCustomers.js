import { useEffect, useMemo, useState } from 'react';
import api from '../../api';
import { useLang } from '../../context/LangContext';
import { formatDate, formatPrice } from '../../utils/format';

export default function AdminCustomers() {
  const { t, lang } = useLang();
  const [customers, setCustomers] = useState([]);
  const [q, setQ] = useState('');
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .get('/admin/customers')
      .then((r) => setCustomers(r.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    if (!term) return customers;
    return customers.filter((c) =>
      [c.fullName, c.email, c.phone].some((v) => (v || '').toLowerCase().includes(term))
    );
  }, [customers, q]);

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.admin.customers}</h1>
      </div>
      <div className="toolbar">
        <input type="search" placeholder={t.admin.filter} value={q} onChange={(e) => setQ(e.target.value)} />
        <span className="muted small">{visible.length}</span>
      </div>
      {error && <div className="alert alert-error">{t.common.error}</div>}
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.admin.name}</th>
              <th>{t.auth.email}</th>
              <th>{t.auth.phone}</th>
              <th>{t.admin.joined}</th>
              <th className="num">{t.admin.orderCount}</th>
              <th className="num">{t.admin.totalSpent}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((c) => (
              <tr key={c.id}>
                <td>
                  <strong>{c.fullName || '—'}</strong>
                  {c.address && <div className="small muted">{c.address}</div>}
                </td>
                <td dir="ltr" className="start-align">{c.email}</td>
                <td dir="ltr" className="start-align">{c.phone || '—'}</td>
                <td className="nowrap">{formatDate(c.createdAt, lang)}</td>
                <td className="num">{c.orderCount}</td>
                <td className="num">{formatPrice(c.totalSpent, lang)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
      </div>
    </div>
  );
}
