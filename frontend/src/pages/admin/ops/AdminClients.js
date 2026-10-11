import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import StatusBadge from '../../../components/StatusBadge';
import { formatDate, formatPrice, localized } from '../../../utils/format';
import { Balance, PaymentForm, SaleForm, StatTile, Tabs } from './shared';

const TYPES = ['CLINIC', 'DOCTOR', 'LAB', 'HOSPITAL', 'OTHER'];
const EMPTY = { name: '', type: 'CLINIC', contactName: '', phone: '', email: '', address: '', city: '', notes: '' };

function ClientForm({ open, client, onClose, onSaved }) {
  const { t } = useLang();
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm(client ? { ...EMPTY, ...Object.fromEntries(Object.entries(client).map(([k, v]) => [k, v ?? ''])) } : EMPTY);
      setError('');
    }
  }, [open, client]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body = {
        name: form.name, type: form.type, contactName: form.contactName, phone: form.phone,
        email: form.email, address: form.address, city: form.city, notes: form.notes,
      };
      const r = client?.id ? await api.put(`/admin/clients/${client.id}`, body) : await api.post('/admin/clients', body);
      onSaved(r.data);
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={client?.id ? t.ops.editClient : t.ops.newClient}>
      <form onSubmit={submit}>
        <div className="grid-2">
          <label className="field span-2">
            <span>{t.ops.clientName}</span>
            <input required autoFocus value={form.name} onChange={set('name')} />
          </label>
          <label className="field">
            <span>{t.ops.type}</span>
            <select value={form.type} onChange={set('type')}>
              {TYPES.map((x) => <option key={x} value={x}>{t.clientType[x]}</option>)}
            </select>
          </label>
          <label className="field">
            <span>{t.ops.contactName}</span>
            <input value={form.contactName} onChange={set('contactName')} />
          </label>
          <label className="field">
            <span>{t.ops.phone}</span>
            <input type="tel" dir="ltr" value={form.phone} onChange={set('phone')} />
          </label>
          <label className="field">
            <span>{t.ops.email}</span>
            <input type="email" dir="ltr" value={form.email} onChange={set('email')} />
          </label>
          <label className="field">
            <span>{t.ops.city}</span>
            <input value={form.city} onChange={set('city')} />
          </label>
          <label className="field">
            <span>{t.ops.address}</span>
            <input value={form.address} onChange={set('address')} />
          </label>
        </div>
        <label className="field">
          <span>{t.ops.notes}</span>
          <textarea rows="2" value={form.notes} onChange={set('notes')} />
        </label>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}

function ClientAccount({ clientId, onClose, onChanged }) {
  const { t, lang } = useLang();
  const [data, setData] = useState(null);
  const [tab, setTab] = useState('orders');
  const [paying, setPaying] = useState(false);
  const [selling, setSelling] = useState(false);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.get(`/admin/clients/${clientId}`).then((r) => setData(r.data)).catch((e) => setError(errorMessage(e, t.common.error)));
  }, [clientId, t.common.error]);

  useEffect(load, [load]);

  const changed = () => {
    load();
    onChanged();
  };

  const removePayment = async (p) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    try {
      await api.delete(`/admin/clients/payments/${p.id}`);
      changed();
    } catch (e) {
      setError(errorMessage(e, t.common.error));
    }
  };

  const s = data?.summary;
  const unpaidOrders = (data?.orders || []).filter((o) => !o.paid && o.status !== 'CANCELLED');

  return (
    <Modal open wide onClose={onClose} title={s ? `${s.name} · ${t.clientType[s.type] || ''}` : t.common.loading}>
      {error && <div className="alert alert-error">{error}</div>}
      {!data ? <p className="muted">{t.common.loading}</p> : (
        <>
          <div className="account-head">
            <div className="small muted">
              {[s.contactName, s.phone, s.email, [s.address, s.city].filter(Boolean).join(', ')].filter(Boolean).join(' · ')}
              {s.notes && <div>{s.notes}</div>}
            </div>
            <div className="row-actions">
              <button className="btn btn-sm btn-primary" onClick={() => setSelling(true)}>+ {t.ops.newSale}</button>
              <button className="btn btn-sm btn-outline" onClick={() => setPaying(true)}>{t.ops.recordPayment}</button>
              <button className="btn btn-sm btn-outline" onClick={() => setEditing(true)}>{t.common.edit}</button>
            </div>
          </div>
          <div className="stat-grid">
            <StatTile label={t.ops.sales} value={formatPrice(s.totalSales, lang)} hint={`${s.orderCount} ${t.ops.orders}`} />
            <StatTile label={t.ops.paid} value={formatPrice(s.totalPaid, lang)} />
            <StatTile label={t.ops.returned} value={formatPrice(s.totalReturned, lang)} />
            <StatTile label={Number(s.balance) < 0 ? t.ops.clientCredit : t.ops.clientOwes}
              value={formatPrice(Math.abs(Number(s.balance)), lang)} tone={Number(s.balance) > 0 ? 'warn' : 'ok'} />
          </div>

          <Tabs active={tab} onChange={setTab} tabs={[
            { key: 'orders', label: t.ops.orders, count: data.orders.length },
            { key: 'payments', label: t.ops.payments, count: data.payments.length },
            { key: 'returns', label: t.ops.returns, count: data.returns.length },
          ]} />

          {tab === 'orders' && (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>#</th><th>{t.ops.date}</th><th>{t.ops.items}</th><th>{t.ops.paymentMethod}</th><th>{t.common.status}</th><th className="num">{t.common.total}</th></tr></thead>
                <tbody>
                  {data.orders.map((o) => (
                    <tr key={o.id}>
                      <td>{o.id}</td>
                      <td className="nowrap">{formatDate(o.createdAt, lang)}</td>
                      <td className="small">{o.items.map((i) => `${i.product ? localized(i.product, 'name', lang) : '—'} × ${i.quantity}`).join(', ')}</td>
                      <td>{t.paymentMethod[o.paymentMethod]}<div><span className={`pill ${o.paid ? 'pill-ok' : 'pill-muted'}`}>{o.paid ? t.orders.paid : t.orders.unpaid}</span></div></td>
                      <td><StatusBadge status={o.status} /></td>
                      <td className="num">{formatPrice(o.totalAmount, lang)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data.orders.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
            </div>
          )}

          {tab === 'payments' && (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>{t.ops.date}</th><th>{t.ops.method}</th><th>{t.ops.forOrder}</th><th>{t.ops.notes}</th><th className="num">{t.ops.amount}</th><th /></tr></thead>
                <tbody>
                  {data.payments.map((p) => (
                    <tr key={p.id}>
                      <td className="nowrap">{formatDate(p.paidAt, lang)}</td>
                      <td>{p.method || '—'}</td>
                      <td>{p.orderId ? `#${p.orderId}` : '—'}</td>
                      <td className="small">{p.notes}</td>
                      <td className="num">{formatPrice(p.amount, lang)}</td>
                      <td className="end"><button className="link-btn danger" onClick={() => removePayment(p)}>{t.common.delete}</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data.payments.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
            </div>
          )}

          {tab === 'returns' && (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>{t.ops.date}</th><th>{t.ops.product}</th><th className="num">{t.ops.qty}</th><th>{t.ops.settlement}</th><th>{t.ops.reason}</th><th className="num">{t.ops.value}</th></tr></thead>
                <tbody>
                  {data.returns.map((r) => (
                    <tr key={r.id}>
                      <td className="nowrap">{formatDate(r.returnDate, lang)}</td>
                      <td>{localized(r.product, 'name', lang)}{r.orderId ? <span className="small muted"> · #{r.orderId}</span> : null}</td>
                      <td className="num">{r.quantity}</td>
                      <td>{t.ops.settlementType[r.settlement]}</td>
                      <td className="small">{r.reason}</td>
                      <td className="num">{formatPrice(r.amount, lang)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data.returns.length === 0 && <p className="muted pad">{t.ops.noReturns}</p>}
            </div>
          )}

          <PaymentForm open={paying} onClose={() => setPaying(false)} kind="client" partyId={s.id} partyName={s.name}
            defaultAmount={Math.max(0, Number(s.balance))}
            links={unpaidOrders.map((o) => ({ id: o.id, label: `#${o.id} · ${formatDate(o.createdAt, lang)} · ${formatPrice(o.totalAmount, lang)}` }))}
            onSaved={changed} />
          <SaleForm open={selling} onClose={() => setSelling(false)} clientId={s.id} onSaved={changed} />
          <ClientForm open={editing} client={s} onClose={() => setEditing(false)} onSaved={changed} />
        </>
      )}
    </Modal>
  );
}

export default function AdminClients() {
  const { t, lang } = useLang();
  const [clients, setClients] = useState([]);
  const [q, setQ] = useState('');
  const [type, setType] = useState('');
  const [owingOnly, setOwingOnly] = useState(false);
  const [creating, setCreating] = useState(false);
  const [selling, setSelling] = useState(false);
  const [openId, setOpenId] = useState(null);
  const [toast, setToast] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api.get('/admin/clients').then((r) => setClients(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);
  useEffect(load, [load]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return clients.filter((c) => {
      if (type && c.type !== type) return false;
      if (owingOnly && !(Number(c.balance) > 0)) return false;
      if (!term) return true;
      return [c.name, c.contactName, c.phone, c.email, c.city].some((v) => (v || '').toLowerCase().includes(term));
    });
  }, [clients, q, type, owingOnly]);

  const totalOwed = clients.reduce((s, c) => s + Math.max(0, Number(c.balance)), 0);

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.clients}</h1>
        <div className="row-actions">
          <button className="btn btn-outline" onClick={() => setSelling(true)}>+ {t.ops.newSale}</button>
          <button className="btn btn-primary" onClick={() => setCreating(true)}>+ {t.ops.newClient}</button>
        </div>
      </div>

      <div className="stat-grid stat-grid-3">
        <StatTile label={t.ops.clients} value={clients.length} />
        <StatTile label={t.ops.sales} value={formatPrice(clients.reduce((s, c) => s + Number(c.totalSales), 0), lang)} />
        <StatTile label={t.ops.clientOwes} value={formatPrice(totalOwed, lang)} tone={totalOwed > 0 ? 'warn' : undefined} />
      </div>

      <div className="toolbar">
        <input type="search" placeholder={t.ops.search} value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">{t.ops.all}</option>
          {TYPES.map((x) => <option key={x} value={x}>{t.clientType[x]}</option>)}
        </select>
        <label className="check inline">
          <input type="checkbox" checked={owingOnly} onChange={(e) => setOwingOnly(e.target.checked)} />
          <span>{t.ops.onlyWithBalance}</span>
        </label>
      </div>
      {toast && <div className="toast">{toast}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.ops.clientName}</th>
              <th>{t.ops.phone}</th>
              <th>{t.ops.city}</th>
              <th className="num">{t.ops.orders}</th>
              <th className="num">{t.ops.sales}</th>
              <th className="num">{t.ops.paid}</th>
              <th className="num">{t.ops.balance}</th>
              <th>{t.ops.lastOrder}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((c) => (
              <tr key={c.id} className="clickable" onClick={() => setOpenId(c.id)}>
                <td>
                  <strong>{c.name}</strong>
                  <div className="small muted">{t.clientType[c.type]}{c.contactName ? ` · ${c.contactName}` : ''}</div>
                </td>
                <td dir="ltr" className="start-align nowrap">{c.phone || '—'}</td>
                <td>{c.city || '—'}</td>
                <td className="num">{c.orderCount}</td>
                <td className="num">{formatPrice(c.totalSales, lang)}</td>
                <td className="num">{formatPrice(c.totalPaid, lang)}</td>
                <td className="num"><Balance value={c.balance} lang={lang} /></td>
                <td className="nowrap">{c.lastOrder ? formatDate(c.lastOrder, lang) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{clients.length ? t.common.noResults : t.ops.noClients}</p>}
      </div>

      <ClientForm open={creating} onClose={() => setCreating(false)} onSaved={(c) => { load(); setOpenId(c.id); }} />
      <SaleForm open={selling} onClose={() => setSelling(false)}
        onSaved={(o) => { load(); setToast(t.ops.saleSaved(o.id)); setTimeout(() => setToast(''), 2500); }} />
      {openId && <ClientAccount clientId={openId} onClose={() => setOpenId(null)} onChanged={load} />}
    </div>
  );
}
