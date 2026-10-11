import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';
import Modal from '../../components/Modal';
import StatusBadge from '../../components/StatusBadge';
import { formatDate, formatPrice, localized } from '../../utils/format';
import { useAuth } from '../../context/AuthContext';
import { SaleForm } from './ops/shared';

const DELIVERY_STATUSES = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const PICKUP_STATUSES = ['PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'PICKED_UP', 'CANCELLED'];
const ALL_STATUSES = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'READY_FOR_PICKUP', 'PICKED_UP', 'CANCELLED'];

function statusesFor(order) {
  return order.deliveryMethod === 'SHOP_PICKUP' ? PICKUP_STATUSES : DELIVERY_STATUSES;
}

export default function AdminOrders() {
  const { t, lang } = useLang();
  const { isAdmin } = useAuth();
  const [selling, setSelling] = useState(false);
  const [orders, setOrders] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api
      .get('/admin/orders')
      .then((r) => setOrders(r.data))
      .catch(() => setError(t.common.error))
      .finally(() => setLoading(false));
  }, [t.common.error]);

  useEffect(load, [load]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return orders.filter((o) => {
      if (statusFilter && o.status !== statusFilter) return false;
      if (!term) return true;
      const cd = o.customerDetails || {};
      return [String(o.id), cd.fullName, cd.email, cd.phone, o.client?.name].some((v) => (v || '').toLowerCase().includes(term));
    });
  }, [orders, statusFilter, q]);

  const updateStatus = async (order, status) => {
    setError('');
    try {
      const r = await api.put(`/admin/orders/${order.id}/status`, { status });
      setOrders((cur) => cur.map((o) => (o.id === order.id ? r.data : o)));
      if (selected?.id === order.id) setSelected(r.data);
      setToast(`#${order.id} · ${t.admin.statusUpdated}`);
      setTimeout(() => setToast(''), 2000);
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    }
  };

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.admin.orders}</h1>
        {isAdmin && <button className="btn btn-primary" onClick={() => setSelling(true)}>+ {t.ops.newSale}</button>}
      </div>
      {isAdmin && (
        <SaleForm open={selling} onClose={() => setSelling(false)}
          onSaved={(o) => { load(); setToast(t.ops.saleSaved(o.id)); setTimeout(() => setToast(''), 2500); }} />
      )}
      <div className="toolbar">
        <input type="search" placeholder={t.admin.filter} value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">{t.admin.allStatuses}</option>
          {ALL_STATUSES.map((s) => <option key={s} value={s}>{t.status[s]}</option>)}
        </select>
        <span className="muted small">{visible.length}</span>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {toast && <div className="toast">{toast}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>{t.common.date}</th>
              <th>{t.admin.customer}</th>
              <th>{t.admin.deliveryCol}</th>
              <th>{t.admin.payment}</th>
              <th className="num">{t.common.total}</th>
              <th>{t.common.status}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((o) => (
              <tr key={o.id} className="clickable" onClick={() => setSelected(o)}>
                <td><strong>{o.id}</strong></td>
                <td className="nowrap">{formatDate(o.createdAt, lang, true)}</td>
                <td>
                  {o.client?.name || o.customerDetails?.fullName || '—'}
                  <div className="small muted">
                    {o.createdByAdmin ? t.ops.staffSale : o.user ? o.user.email : t.admin.guest}
                    {o.client && o.customerDetails?.phone ? ` · ${o.customerDetails.phone}` : ''}
                  </div>
                </td>
                <td>{t.deliveryMethod[o.deliveryMethod]}</td>
                <td>
                  {t.paymentMethod[o.paymentMethod]}
                  <div><span className={`pill ${o.paid ? 'pill-ok' : 'pill-muted'}`}>{o.paid ? t.orders.paid : t.orders.unpaid}</span></div>
                </td>
                <td className="num">{formatPrice(o.totalAmount, lang)}</td>
                <td onClick={(e) => e.stopPropagation()}>
                  <select
                    className={`status-select status-${o.status.toLowerCase()}`}
                    value={o.status}
                    disabled={o.status === 'CANCELLED'}
                    onChange={(e) => updateStatus(o, e.target.value)}
                    aria-label={t.admin.updateStatus}
                  >
                    {statusesFor(o).map((s) => <option key={s} value={s}>{t.status[s]}</option>)}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
      </div>

      <Modal open={!!selected} wide title={selected ? `${t.admin.orderDetails} #${selected.id}` : ''} onClose={() => setSelected(null)}>
        {selected && (
          <div className="order-detail">
            <div className="grid-2">
              <div>
                <h4>{t.admin.contact}</h4>
                <p>
                  <strong>{selected.customerDetails?.fullName}</strong><br />
                  <span dir="ltr">{selected.customerDetails?.email}</span><br />
                  <span dir="ltr">{selected.customerDetails?.phone}</span>
                </p>
                <p className="small muted">{selected.user ? selected.user.email : t.admin.guest}</p>
              </div>
              <div>
                <h4>{t.admin.deliveryCol}</h4>
                <p>
                  {t.deliveryMethod[selected.deliveryMethod]}<br />
                  {selected.customerDetails?.deliveryAddress || (selected.deliveryMethod === 'SHOP_PICKUP' ? t.orders.pickupAtShop : '—')}
                </p>
                <h4>{t.admin.payment}</h4>
                <p>
                  {t.paymentMethod[selected.paymentMethod]} ·{' '}
                  <span className={`pill ${selected.paid ? 'pill-ok' : 'pill-muted'}`}>{selected.paid ? t.orders.paid : t.orders.unpaid}</span>
                  {selected.paymentIntentId && <><br /><code className="small">{selected.paymentIntentId}</code></>}
                </p>
              </div>
            </div>
            <table className="table">
              <thead>
                <tr>
                  <th>{t.admin.name}</th>
                  <th className="num">{t.common.quantity}</th>
                  <th className="num">{t.common.price}</th>
                  <th className="num">{t.common.total}</th>
                </tr>
              </thead>
              <tbody>
                {selected.items.map((it) => (
                  <tr key={it.id}>
                    <td>{it.product ? localized(it.product, 'name', lang) : '—'}</td>
                    <td className="num">{it.quantity}</td>
                    <td className="num">{formatPrice(it.price, lang)}</td>
                    <td className="num">{formatPrice(it.price * it.quantity, lang)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan="3"><strong>{t.common.total}</strong></td>
                  <td className="num"><strong>{formatPrice(selected.totalAmount, lang)}</strong></td>
                </tr>
              </tfoot>
            </table>
            <div className="row-actions">
              <StatusBadge status={selected.status} />
              <select
                value={selected.status}
                disabled={selected.status === 'CANCELLED'}
                onChange={(e) => updateStatus(selected, e.target.value)}
                aria-label={t.admin.updateStatus}
              >
                {statusesFor(selected).map((s) => <option key={s} value={s}>{t.status[s]}</option>)}
              </select>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
