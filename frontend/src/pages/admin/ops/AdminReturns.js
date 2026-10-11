import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatDate, formatPrice, localized, todayIso } from '../../../utils/format';
import { ProductSelect, StatTile, useClients, useProviders, useStockProducts } from './shared';

function ReturnForm({ open, onClose, onSaved }) {
  const { t, lang } = useLang();
  const { products, reload: reloadProducts } = useStockProducts();
  const { clients } = useClients();
  const { providers } = useProviders();
  const [form, setForm] = useState(null);
  const [amountTouched, setAmountTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      reloadProducts();
      setForm({ type: 'CUSTOMER_RETURN', returnDate: todayIso(), clientId: '', orderId: '', supplierId: '',
        productId: '', quantity: 1, amount: '', settlement: 'CREDIT', backToStock: true, reason: '' });
      setAmountTouched(false);
      setError('');
    }
  }, [open, reloadProducts]);

  const product = useMemo(() => products.find((p) => String(p.id) === form?.productId), [products, form?.productId]);

  // Suggest a value: sell price for client returns, cost price for provider returns.
  useEffect(() => {
    if (!form || amountTouched || !product) return;
    const unit = form.type === 'CUSTOMER_RETURN' ? product.price : (product.costPrice ?? product.price);
    const suggested = ((Number(unit) || 0) * (Number(form.quantity) || 0)).toFixed(2);
    if (suggested !== form.amount) setForm((f) => ({ ...f, amount: suggested }));
  }, [form, product, amountTouched]);

  if (!open || !form) return null;
  const isClient = form.type === 'CUSTOMER_RETURN';
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api.post('/admin/returns', {
        type: form.type,
        returnDate: form.returnDate,
        productId: Number(form.productId),
        quantity: Number(form.quantity),
        amount: Number(form.amount) || 0,
        settlement: form.settlement,
        clientId: isClient && form.clientId ? Number(form.clientId) : null,
        orderId: isClient && form.orderId ? Number(String(form.orderId).replace('#', '')) : null,
        supplierId: !isClient && form.supplierId ? Number(form.supplierId) : null,
        backToStock: isClient ? form.backToStock : false,
        reason: form.reason,
      });
      onSaved?.();
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} wide title={t.ops.newReturn}>
      <form onSubmit={submit}>
        <div className="segmented" role="radiogroup">
          {['CUSTOMER_RETURN', 'SUPPLIER_RETURN'].map((tp) => (
            <button type="button" key={tp} role="radio" aria-checked={form.type === tp}
              className={form.type === tp ? 'active' : ''}
              onClick={() => { setForm({ ...form, type: tp }); setAmountTouched(false); }}>
              {tp === 'CUSTOMER_RETURN' ? t.ops.fromClient : t.ops.toProvider}
            </button>
          ))}
        </div>

        <div className="grid-2">
          {isClient ? (
            <>
              <label className="field">
                <span>{t.ops.client}</span>
                <select required value={form.clientId} onChange={set('clientId')}>
                  <option value="">{t.ops.chooseClient}</option>
                  {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label className="field">
                <span>{t.ops.orderNo}</span>
                <input dir="ltr" inputMode="numeric" value={form.orderId} onChange={set('orderId')} placeholder="#" />
              </label>
            </>
          ) : (
            <label className="field span-2">
              <span>{t.ops.provider}</span>
              <select required value={form.supplierId} onChange={set('supplierId')}>
                <option value="">{t.ops.chooseProvider}</option>
                {providers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </label>
          )}
          <label className="field span-2">
            <span>{t.ops.product}</span>
            <ProductSelect products={products} value={form.productId} required
              onChange={(v) => setForm({ ...form, productId: v })} />
          </label>
          <label className="field">
            <span>{t.ops.qty}</span>
            <input type="number" min="1" step="1" dir="ltr" required value={form.quantity} onChange={set('quantity')} />
          </label>
          <label className="field">
            <span>{t.ops.value} ({t.ops.egp})</span>
            <input type="number" min="0" step="0.01" dir="ltr" required value={form.amount}
              onChange={(e) => { setAmountTouched(true); setForm({ ...form, amount: e.target.value }); }} />
          </label>
          <label className="field">
            <span>{t.ops.settlement}</span>
            <select value={form.settlement} onChange={set('settlement')}>
              <option value="CREDIT">{t.ops.settlementType.CREDIT}</option>
              <option value="CASH">{t.ops.settlementType.CASH}</option>
            </select>
          </label>
          <label className="field">
            <span>{t.ops.date}</span>
            <input type="date" required value={form.returnDate} onChange={set('returnDate')} />
          </label>
        </div>
        <p className="small muted">{isClient ? t.ops.settlementHintClient : t.ops.settlementHintProvider}</p>

        {isClient && (
          <label className="check">
            <input type="checkbox" checked={form.backToStock} onChange={(e) => setForm({ ...form, backToStock: e.target.checked })} />
            <span>{t.ops.backToStock}</span>
          </label>
        )}
        <label className="field">
          <span>{t.ops.reason}</span>
          <input value={form.reason} onChange={set('reason')} />
        </label>
        {product && <p className="small muted">{t.ops.inStock}: {product.stockQuantity} · {t.ops.sellPrice}: {formatPrice(product.price, lang)}{product.costPrice != null ? ` · ${t.ops.costPrice}: ${formatPrice(product.costPrice, lang)}` : ''}</p>}
        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}

export default function AdminReturns() {
  const { t, lang } = useLang();
  const [returns, setReturns] = useState([]);
  const [type, setType] = useState('');
  const [q, setQ] = useState('');
  const [creating, setCreating] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api.get('/admin/returns').then((r) => setReturns(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);
  useEffect(load, [load]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return returns.filter((r) => {
      if (type && r.type !== type) return false;
      if (!term) return true;
      return [r.product?.name, r.product?.sku, r.client?.name, r.supplier?.name, r.reason, r.orderId && String(r.orderId)]
        .some((v) => (v || '').toLowerCase().includes(term));
    });
  }, [returns, type, q]);

  const sumOf = (tp) => returns.filter((r) => r.type === tp).reduce((s, r) => s + Number(r.amount), 0);

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.returns}</h1>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ {t.ops.newReturn}</button>
      </div>

      <div className="stat-grid stat-grid-3">
        <StatTile label={t.ops.returnType.CUSTOMER_RETURN} value={formatPrice(sumOf('CUSTOMER_RETURN'), lang)}
          hint={`${returns.filter((r) => r.type === 'CUSTOMER_RETURN').length}`} />
        <StatTile label={t.ops.returnType.SUPPLIER_RETURN} value={formatPrice(sumOf('SUPPLIER_RETURN'), lang)}
          hint={`${returns.filter((r) => r.type === 'SUPPLIER_RETURN').length}`} />
      </div>

      <div className="toolbar">
        <input type="search" placeholder={t.ops.search} value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">{t.ops.all}</option>
          <option value="CUSTOMER_RETURN">{t.ops.returnType.CUSTOMER_RETURN}</option>
          <option value="SUPPLIER_RETURN">{t.ops.returnType.SUPPLIER_RETURN}</option>
        </select>
      </div>

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>{t.ops.date}</th>
              <th>{t.ops.type}</th>
              <th>{t.ops.client} / {t.ops.provider}</th>
              <th>{t.ops.product}</th>
              <th className="num">{t.ops.qty}</th>
              <th className="num">{t.ops.value}</th>
              <th>{t.ops.settlement}</th>
              <th>{t.ops.reason}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td className="nowrap">{formatDate(r.returnDate, lang)}</td>
                <td>
                  <span className={`pill ${r.type === 'CUSTOMER_RETURN' ? '' : 'pill-muted'}`}>{t.ops.returnType[r.type]}</span>
                </td>
                <td>
                  {r.type === 'CUSTOMER_RETURN' ? r.client?.name : r.supplier?.name}
                  {r.orderId ? <span className="small muted"> · #{r.orderId}</span> : null}
                </td>
                <td>
                  {localized(r.product, 'name', lang)}
                  {r.type === 'CUSTOMER_RETURN' && !r.backToStock && <div className="small warn">✕ {t.ops.backToStock}</div>}
                </td>
                <td className="num">{r.quantity}</td>
                <td className="num">{formatPrice(r.amount, lang)}</td>
                <td>{t.ops.settlementType[r.settlement]}</td>
                <td className="small">{r.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.ops.noReturns}</p>}
      </div>

      <ReturnForm open={creating} onClose={() => setCreating(false)} onSaved={load} />
    </div>
  );
}
