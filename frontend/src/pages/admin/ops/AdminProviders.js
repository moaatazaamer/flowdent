import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatDate, formatPrice, localized } from '../../../utils/format';
import { Balance, PaymentForm, StatTile, Tabs } from './shared';
import { PurchaseForm, PurchaseStatusBadge } from './AdminPurchases';

const EMPTY = { name: '', contactName: '', phone: '', email: '', address: '', notes: '', active: true };

function ProviderForm({ open, provider, onClose, onSaved }) {
  const { t } = useLang();
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm(provider
        ? { ...EMPTY, ...Object.fromEntries(Object.entries(provider).map(([k, v]) => [k, v ?? ''])), active: provider.active !== false }
        : EMPTY);
      setError('');
    }
  }, [open, provider]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body = { name: form.name, contactName: form.contactName, phone: form.phone, email: form.email,
        address: form.address, notes: form.notes, active: form.active };
      const r = provider?.id ? await api.put(`/admin/suppliers/${provider.id}`, body) : await api.post('/admin/suppliers', body);
      onSaved(r.data);
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={provider?.id ? t.ops.editProvider : t.ops.newProvider}>
      <form onSubmit={submit}>
        <div className="grid-2">
          <label className="field span-2">
            <span>{t.ops.providerName}</span>
            <input required autoFocus value={form.name} onChange={set('name')} />
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
            <span>{t.ops.address}</span>
            <input value={form.address} onChange={set('address')} />
          </label>
        </div>
        <label className="field">
          <span>{t.ops.notes}</span>
          <textarea rows="2" value={form.notes} onChange={set('notes')} />
        </label>
        <label className="check">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span>{t.ops.active}</span>
        </label>
        <p className="small muted">{t.ops.deactivateHint}</p>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}

function ProviderAccount({ providerId, onClose, onChanged }) {
  const { t, lang } = useLang();
  const [data, setData] = useState(null);
  const [tab, setTab] = useState('purchases');
  const [paying, setPaying] = useState(false);
  const [buying, setBuying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.get(`/admin/suppliers/${providerId}`).then((r) => setData(r.data)).catch((e) => setError(errorMessage(e, t.common.error)));
  }, [providerId, t.common.error]);
  useEffect(load, [load]);

  const changed = () => {
    load();
    onChanged();
  };

  const removePayment = async (p) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    try {
      await api.delete(`/admin/suppliers/payments/${p.id}`);
      changed();
    } catch (e) {
      setError(errorMessage(e, t.common.error));
    }
  };

  const s = data?.summary;

  return (
    <Modal open wide onClose={onClose} title={s ? s.name : t.common.loading}>
      {error && <div className="alert alert-error">{error}</div>}
      {!data ? <p className="muted">{t.common.loading}</p> : (
        <>
          <div className="account-head">
            <div className="small muted">
              {[s.contactName, s.phone, s.email, s.address].filter(Boolean).join(' · ')}
              {s.notes && <div>{s.notes}</div>}
              {!s.active && <span className="pill pill-muted">{t.ops.inactive}</span>}
            </div>
            <div className="row-actions">
              <button className="btn btn-sm btn-primary" onClick={() => setBuying(true)}>+ {t.ops.newPurchase}</button>
              <button className="btn btn-sm btn-outline" onClick={() => setPaying(true)}>{t.ops.recordPayment}</button>
              <button className="btn btn-sm btn-outline" onClick={() => setEditing(true)}>{t.common.edit}</button>
            </div>
          </div>
          <div className="stat-grid">
            <StatTile label={t.ops.bought} value={formatPrice(s.totalBought, lang)} hint={`${s.purchaseCount} ${t.ops.purchases}`} />
            <StatTile label={t.ops.paid} value={formatPrice(s.totalPaid, lang)} />
            <StatTile label={t.ops.returned} value={formatPrice(s.totalReturned, lang)} />
            <StatTile label={t.ops.weOwe} value={formatPrice(s.balance, lang)} tone={Number(s.balance) > 0 ? 'warn' : 'ok'} />
          </div>

          <Tabs active={tab} onChange={setTab} tabs={[
            { key: 'purchases', label: t.ops.purchases, count: data.purchases.length },
            { key: 'payments', label: t.ops.payments, count: data.payments.length },
            { key: 'returns', label: t.ops.returns, count: data.returns.length },
          ]} />

          {tab === 'purchases' && (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>#</th><th>{t.ops.date}</th><th>{t.ops.invoice}</th><th>{t.ops.items}</th><th>{t.common.status}</th><th className="num">{t.ops.goods}</th><th className="num">{t.ops.extraCosts}</th></tr></thead>
                <tbody>
                  {data.purchases.map((p) => (
                    <tr key={p.id}>
                      <td>{p.id}</td>
                      <td className="nowrap">{formatDate(p.purchaseDate, lang)}</td>
                      <td>{p.invoiceNumber || '—'}</td>
                      <td className="small">{p.items.map((i) => `${localized(i.product, 'name', lang)} × ${i.quantity}`).join(', ')}</td>
                      <td><PurchaseStatusBadge status={p.status} /></td>
                      <td className="num">{formatPrice(p.goodsTotal, lang)}</td>
                      <td className="num">{formatPrice(p.extraCosts, lang)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data.purchases.length === 0 && <p className="muted pad">{t.ops.noPurchases}</p>}
            </div>
          )}

          {tab === 'payments' && (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>{t.ops.date}</th><th>{t.ops.method}</th><th>{t.ops.forPurchase}</th><th>{t.ops.notes}</th><th className="num">{t.ops.amount}</th><th /></tr></thead>
                <tbody>
                  {data.payments.map((p) => (
                    <tr key={p.id}>
                      <td className="nowrap">{formatDate(p.paidAt, lang)}</td>
                      <td>{p.method || '—'}</td>
                      <td>{p.purchaseId ? `#${p.purchaseId}` : '—'}</td>
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
                      <td>{localized(r.product, 'name', lang)}</td>
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

          <PaymentForm open={paying} onClose={() => setPaying(false)} kind="provider" partyId={s.id} partyName={s.name}
            defaultAmount={Math.max(0, Number(s.balance))}
            links={data.purchases.filter((p) => p.status !== 'CANCELLED')
              .map((p) => ({ id: p.id, label: `#${p.id} · ${formatDate(p.purchaseDate, lang)} · ${formatPrice(p.goodsTotal, lang)}` }))}
            onSaved={changed} />
          <PurchaseForm open={buying} supplierId={s.id} onClose={() => setBuying(false)} onSaved={changed} />
          <ProviderForm open={editing} provider={s} onClose={() => setEditing(false)} onSaved={changed} />
        </>
      )}
    </Modal>
  );
}

export default function AdminProviders() {
  const { t, lang } = useLang();
  const [providers, setProviders] = useState([]);
  const [q, setQ] = useState('');
  const [owingOnly, setOwingOnly] = useState(false);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api.get('/admin/suppliers').then((r) => setProviders(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);
  useEffect(load, [load]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return providers.filter((p) => {
      if (owingOnly && !(Number(p.balance) > 0)) return false;
      if (!term) return true;
      return [p.name, p.contactName, p.phone, p.email].some((v) => (v || '').toLowerCase().includes(term));
    });
  }, [providers, q, owingOnly]);

  const totalOwed = providers.reduce((s, p) => s + Math.max(0, Number(p.balance)), 0);

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.providers}</h1>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ {t.ops.newProvider}</button>
      </div>

      <div className="stat-grid stat-grid-3">
        <StatTile label={t.ops.providers} value={providers.length} />
        <StatTile label={t.ops.bought} value={formatPrice(providers.reduce((s, p) => s + Number(p.totalBought), 0), lang)} />
        <StatTile label={t.ops.youOwe} value={formatPrice(totalOwed, lang)} tone={totalOwed > 0 ? 'warn' : undefined} />
      </div>

      <div className="toolbar">
        <input type="search" placeholder={t.ops.search} value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="check inline">
          <input type="checkbox" checked={owingOnly} onChange={(e) => setOwingOnly(e.target.checked)} />
          <span>{t.ops.onlyWithBalance}</span>
        </label>
      </div>

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.ops.providerName}</th>
              <th>{t.ops.phone}</th>
              <th className="num">{t.ops.purchases}</th>
              <th className="num">{t.ops.bought}</th>
              <th className="num">{t.ops.paid}</th>
              <th className="num">{t.ops.weOwe}</th>
              <th>{t.ops.lastPurchase}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.id} className={`clickable ${p.active ? '' : 'row-muted'}`} onClick={() => setOpenId(p.id)}>
                <td>
                  <strong>{p.name}</strong>
                  <div className="small muted">{p.contactName}{!p.active ? ` · ${t.ops.inactive}` : ''}</div>
                </td>
                <td dir="ltr" className="start-align nowrap">{p.phone || '—'}</td>
                <td className="num">{p.purchaseCount}</td>
                <td className="num">{formatPrice(p.totalBought, lang)}</td>
                <td className="num">{formatPrice(p.totalPaid, lang)}</td>
                <td className="num"><Balance value={p.balance} lang={lang} /></td>
                <td className="nowrap">{p.lastPurchase ? formatDate(p.lastPurchase, lang) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{providers.length ? t.common.noResults : t.ops.noProviders}</p>}
      </div>

      <ProviderForm open={creating} onClose={() => setCreating(false)} onSaved={(p) => { load(); setOpenId(p.id); }} />
      {openId && <ProviderAccount providerId={openId} onClose={() => setOpenId(null)} onChanged={load} />}
    </div>
  );
}
