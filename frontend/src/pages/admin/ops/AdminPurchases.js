import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatDate, formatPrice, localized, todayIso } from '../../../utils/format';
import { LineItems, linesTotal, StatTile, useProviders, useStockProducts } from './shared';

export function PurchaseStatusBadge({ status }) {
  const { t } = useLang();
  const cls = status === 'RECEIVED' ? 'status-delivered' : status === 'CANCELLED' ? 'status-cancelled' : 'status-pending';
  return <span className={`status ${cls}`}>{t.ops.purchaseStatus[status] || status}</span>;
}

/** Create or edit a purchase. Pass `purchase` to edit an ORDERED one, `supplierId` to preselect a provider. */
export function PurchaseForm({ open, onClose, onSaved, purchase, supplierId }) {
  const { t, lang } = useLang();
  const { products, reload: reloadProducts } = useStockProducts();
  const { providers } = useProviders();
  const [form, setForm] = useState(null);
  const [lines, setLines] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    reloadProducts();
    setError('');
    if (purchase) {
      setForm({
        supplierId: String(purchase.supplier.id), purchaseDate: purchase.purchaseDate,
        invoiceNumber: purchase.invoiceNumber || '', shippingCost: purchase.shippingCost || '',
        customsCost: purchase.customsCost || '', otherCost: purchase.otherCost || '', notes: purchase.notes || '',
        receivedNow: false, amountPaid: '', paymentMethod: t.ops.paymentMethods[0],
      });
      setLines(purchase.items.map((i) => ({ productId: String(i.product.id), quantity: i.quantity, price: String(i.unitCost) })));
    } else {
      setForm({
        supplierId: supplierId ? String(supplierId) : '', purchaseDate: todayIso(), invoiceNumber: '',
        shippingCost: '', customsCost: '', otherCost: '', notes: '', receivedNow: true,
        amountPaid: '', paymentMethod: t.ops.paymentMethods[0],
      });
      setLines([{ productId: '', quantity: 1, price: '' }]);
    }
  }, [open, purchase, supplierId, reloadProducts, t.ops.paymentMethods]);

  if (!open || !form) return null;

  const goods = linesTotal(lines);
  const extra = (Number(form.shippingCost) || 0) + (Number(form.customsCost) || 0) + (Number(form.otherCost) || 0);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const activeProviders = providers.filter((p) => p.active || String(p.id) === form.supplierId);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body = {
        supplierId: Number(form.supplierId),
        purchaseDate: form.purchaseDate,
        invoiceNumber: form.invoiceNumber,
        shippingCost: Number(form.shippingCost) || 0,
        customsCost: Number(form.customsCost) || 0,
        otherCost: Number(form.otherCost) || 0,
        notes: form.notes,
        receivedNow: form.receivedNow,
        amountPaid: purchase ? null : Number(form.amountPaid) || 0,
        paymentMethod: form.paymentMethod,
        items: lines.map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity), unitCost: Number(l.price) })),
      };
      const r = purchase ? await api.put(`/admin/purchases/${purchase.id}`, body) : await api.post('/admin/purchases', body);
      onSaved?.(r.data);
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} wide onClose={onClose} title={purchase ? `${t.ops.editPurchase} #${purchase.id}` : t.ops.newPurchase}>
      <form onSubmit={submit}>
        <div className="grid-3">
          <label className="field">
            <span>{t.ops.provider}</span>
            <select required value={form.supplierId} onChange={set('supplierId')}>
              <option value="">{t.ops.chooseProvider}</option>
              {activeProviders.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </label>
          <label className="field">
            <span>{t.ops.date}</span>
            <input type="date" required value={form.purchaseDate} onChange={set('purchaseDate')} />
          </label>
          <label className="field">
            <span>{t.ops.invoice}</span>
            <input dir="ltr" value={form.invoiceNumber} onChange={set('invoiceNumber')} />
          </label>
        </div>

        <LineItems lines={lines} setLines={setLines} products={products} priceLabel={`${t.ops.unitCost} (${t.ops.egp})`} priceField="costPrice" />

        <h4 className="sub-heading">{t.ops.extraCosts}</h4>
        <div className="grid-3">
          <label className="field">
            <span>{t.ops.shipping}</span>
            <input type="number" min="0" step="0.01" dir="ltr" placeholder="0" value={form.shippingCost} onChange={set('shippingCost')} />
          </label>
          <label className="field">
            <span>{t.ops.customs}</span>
            <input type="number" min="0" step="0.01" dir="ltr" placeholder="0" value={form.customsCost} onChange={set('customsCost')} />
          </label>
          <label className="field">
            <span>{t.ops.otherCost}</span>
            <input type="number" min="0" step="0.01" dir="ltr" placeholder="0" value={form.otherCost} onChange={set('otherCost')} />
          </label>
        </div>
        <p className="small muted">{t.ops.extraCostsHint}</p>

        <label className="field">
          <span>{t.ops.notes}</span>
          <input value={form.notes} onChange={set('notes')} />
        </label>

        <label className="check">
          <input type="checkbox" checked={form.receivedNow} onChange={(e) => setForm({ ...form, receivedNow: e.target.checked })} />
          <span>{t.ops.receivedNow}</span>
        </label>

        {!purchase && (
          <div className="grid-2">
            <label className="field">
              <span>{t.ops.paidToProvider} ({t.ops.egp})</span>
              <input type="number" min="0" step="0.01" dir="ltr" placeholder="0" value={form.amountPaid} onChange={set('amountPaid')} />
            </label>
            <label className="field">
              <span>{t.ops.method}</span>
              <input list="purchase-pay-methods" value={form.paymentMethod} onChange={set('paymentMethod')} />
              <datalist id="purchase-pay-methods">
                {t.ops.paymentMethods.map((m) => <option key={m} value={m} />)}
              </datalist>
            </label>
          </div>
        )}

        <div className="summary-row"><span>{t.ops.goods}</span><span>{formatPrice(goods, lang)}</span></div>
        <div className="summary-row"><span>{t.ops.extraCosts}</span><span>{formatPrice(extra, lang)}</span></div>
        <div className="summary-row total"><span>{t.ops.totalCost}</span><strong>{formatPrice(goods + extra, lang)}</strong></div>

        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}

function PurchaseDetail({ purchase, onClose, onAction, onEdit }) {
  const { t, lang } = useLang();
  const p = purchase;
  return (
    <Modal open wide onClose={onClose} title={`${t.ops.purchase} #${p.id} · ${p.supplier.name}`}>
      <div className="account-head">
        <div className="small muted">
          {formatDate(p.purchaseDate, lang)}
          {p.invoiceNumber ? ` · ${t.ops.invoice} ${p.invoiceNumber}` : ''}
          {p.receivedDate ? ` · ${t.ops.receivedOn} ${formatDate(p.receivedDate, lang)}` : ''}
          {p.notes && <div>{p.notes}</div>}
        </div>
        <div className="row-actions">
          <PurchaseStatusBadge status={p.status} />
          {p.status === 'ORDERED' && (
            <>
              <button className="btn btn-sm btn-primary" onClick={() => onAction(p, 'receive')}>{t.ops.receive}</button>
              <button className="btn btn-sm btn-outline" onClick={() => onEdit(p)}>{t.common.edit}</button>
              <button className="btn btn-sm btn-outline danger-text" onClick={() => onAction(p, 'cancel')}>{t.ops.cancelPurchase}</button>
            </>
          )}
        </div>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>{t.ops.product}</th>
              <th className="num">{t.ops.qty}</th>
              <th className="num">{t.ops.unitCost}</th>
              <th className="num">{t.ops.landedCost}</th>
              <th className="num">{t.ops.lineTotal}</th>
            </tr>
          </thead>
          <tbody>
            {p.items.map((i) => (
              <tr key={i.id}>
                <td>{i.product.sku ? <span className="muted small">{i.product.sku} · </span> : null}{localized(i.product, 'name', lang)}</td>
                <td className="num">{i.quantity}</td>
                <td className="num">{formatPrice(i.unitCost, lang)}</td>
                <td className="num">{i.landedUnitCost != null ? formatPrice(i.landedUnitCost, lang) : '—'}</td>
                <td className="num">{formatPrice(i.unitCost * i.quantity, lang)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="summary-box">
        <div className="summary-row"><span>{t.ops.goods}</span><span>{formatPrice(p.goodsTotal, lang)}</span></div>
        <div className="summary-row small muted"><span>{t.ops.shipping}</span><span>{formatPrice(p.shippingCost, lang)}</span></div>
        <div className="summary-row small muted"><span>{t.ops.customs}</span><span>{formatPrice(p.customsCost, lang)}</span></div>
        <div className="summary-row small muted"><span>{t.ops.otherCost}</span><span>{formatPrice(p.otherCost, lang)}</span></div>
        <div className="summary-row total"><span>{t.ops.totalCost}</span><strong>{formatPrice(p.totalCost, lang)}</strong></div>
      </div>
    </Modal>
  );
}

export default function AdminPurchases() {
  const { t, lang } = useLang();
  const [purchases, setPurchases] = useState([]);
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api.get('/admin/purchases').then((r) => setPurchases(r.data))
      .catch(() => setError(t.common.error)).finally(() => setLoading(false));
  }, [t.common.error]);
  useEffect(load, [load]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return purchases.filter((p) => {
      if (status && p.status !== status) return false;
      if (!term) return true;
      return [String(p.id), p.supplier.name, p.invoiceNumber].some((v) => (v || '').toLowerCase().includes(term))
        || p.items.some((i) => (i.product.name || '').toLowerCase().includes(term) || (i.product.sku || '').toLowerCase().includes(term));
    });
  }, [purchases, status, q]);

  const active = purchases.filter((p) => p.status !== 'CANCELLED');
  const sum = (list, f) => list.reduce((s, p) => s + Number(p[f] || 0), 0);

  const action = async (p, kind) => {
    if (!window.confirm(kind === 'receive' ? t.ops.receiveConfirm : t.ops.cancelConfirm)) return;
    setError('');
    try {
      const r = await api.post(`/admin/purchases/${p.id}/${kind}`, kind === 'receive' ? {} : undefined);
      setPurchases((cur) => cur.map((x) => (x.id === p.id ? r.data : x)));
      if (selected?.id === p.id) setSelected(r.data);
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    }
  };

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.purchases}</h1>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ {t.ops.newPurchase}</button>
      </div>

      <div className="stat-grid stat-grid-3">
        <StatTile label={t.ops.goods} value={formatPrice(sum(active, 'goodsTotal'), lang)} hint={`${active.length} ${t.ops.purchases}`} />
        <StatTile label={t.ops.extraCosts} value={formatPrice(sum(active, 'extraCosts'), lang)} />
        <StatTile label={t.ops.purchaseStatus.ORDERED} value={purchases.filter((p) => p.status === 'ORDERED').length} />
      </div>

      <div className="toolbar">
        <input type="search" placeholder={t.ops.search} value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">{t.ops.all}</option>
          {['ORDERED', 'RECEIVED', 'CANCELLED'].map((s) => <option key={s} value={s}>{t.ops.purchaseStatus[s]}</option>)}
        </select>
      </div>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>{t.ops.date}</th>
              <th>{t.ops.provider}</th>
              <th>{t.ops.invoice}</th>
              <th className="num">{t.ops.items}</th>
              <th className="num">{t.ops.goods}</th>
              <th className="num">{t.ops.extraCosts}</th>
              <th>{t.common.status}</th>
              <th className="end">{t.common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.id} className="clickable" onClick={() => setSelected(p)}>
                <td><strong>{p.id}</strong></td>
                <td className="nowrap">{formatDate(p.purchaseDate, lang)}</td>
                <td>{p.supplier.name}</td>
                <td>{p.invoiceNumber || '—'}</td>
                <td className="num">{p.items.reduce((s, i) => s + i.quantity, 0)}</td>
                <td className="num">{formatPrice(p.goodsTotal, lang)}</td>
                <td className="num">{formatPrice(p.extraCosts, lang)}</td>
                <td><PurchaseStatusBadge status={p.status} /></td>
                <td className="end nowrap" onClick={(e) => e.stopPropagation()}>
                  {p.status === 'ORDERED' && (
                    <>
                      <button className="link-btn" onClick={() => action(p, 'receive')}>{t.ops.receive}</button>
                      <button className="link-btn" onClick={() => setEditing(p)}>{t.common.edit}</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.ops.noPurchases}</p>}
      </div>

      <PurchaseForm open={creating} onClose={() => setCreating(false)} onSaved={load} />
      <PurchaseForm open={!!editing} purchase={editing} onClose={() => setEditing(null)}
        onSaved={(p) => { load(); if (selected?.id === p.id) setSelected(p); }} />
      {selected && (
        <PurchaseDetail purchase={selected} onClose={() => setSelected(null)} onAction={action}
          onEdit={(p) => setEditing(p)} />
      )}
    </div>
  );
}
