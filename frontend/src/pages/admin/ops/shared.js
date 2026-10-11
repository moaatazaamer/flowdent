import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatPrice, localized, todayIso } from '../../../utils/format';

/* ------------------------------------------------------------------ small UI */

export function StatTile({ label, value, hint, tone }) {
  return (
    <div className={`stat card ${tone ? `stat-${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint && <span className="small muted">{hint}</span>}
    </div>
  );
}

export function Tabs({ tabs, active, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((tb) => (
        <button
          key={tb.key}
          role="tab"
          aria-selected={active === tb.key}
          className={`tab ${active === tb.key ? 'active' : ''}`}
          onClick={() => onChange(tb.key)}
        >
          {tb.label}
          {tb.count != null && <span className="tab-count">{tb.count}</span>}
        </button>
      ))}
    </div>
  );
}

/** Coloured balance: positive = owed, negative = credit. */
export function Balance({ value, lang }) {
  const n = Number(value || 0);
  const cls = n > 0.004 ? 'bal-owed' : n < -0.004 ? 'bal-credit' : 'bal-zero';
  return <span className={`bal ${cls}`}>{formatPrice(n, lang)}</span>;
}

/* ------------------------------------------------------------------ data hooks */

/** Products with cost and stock (admin view). */
export function useStockProducts() {
  const [products, setProducts] = useState([]);
  const reload = useCallback(() => {
    api.get('/admin/stock').then((r) => setProducts(r.data)).catch(() => {});
  }, []);
  useEffect(reload, [reload]);
  return { products, reload };
}

export function useClients() {
  const [clients, setClients] = useState([]);
  const reload = useCallback(() => {
    api.get('/admin/clients').then((r) => setClients(r.data)).catch(() => {});
  }, []);
  useEffect(reload, [reload]);
  return { clients, reload };
}

export function useProviders() {
  const [providers, setProviders] = useState([]);
  const reload = useCallback(() => {
    api.get('/admin/suppliers').then((r) => setProviders(r.data)).catch(() => {});
  }, []);
  useEffect(reload, [reload]);
  return { providers, reload };
}

/* ------------------------------------------------------------------ product picker */

export function ProductSelect({ products, value, onChange, required, showStock = true }) {
  const { t, lang } = useLang();
  return (
    <select value={value || ''} onChange={(e) => onChange(e.target.value)} required={required}>
      <option value="">{t.ops.chooseProduct}</option>
      {products.map((p) => (
        <option key={p.id} value={p.id}>
          {(p.sku ? `${p.sku} · ` : '') + localized(p, 'name', lang)}
          {showStock ? ` (${p.stockQuantity})` : ''}
        </option>
      ))}
    </select>
  );
}

/**
 * Editable list of product lines: [{productId, quantity, price}].
 * priceField picks the default price when a product is chosen ('price' or 'costPrice').
 */
export function LineItems({ lines, setLines, products, priceLabel, priceField }) {
  const { t, lang } = useLang();
  const byId = useMemo(() => Object.fromEntries(products.map((p) => [String(p.id), p])), [products]);

  const update = (i, patch) => setLines(lines.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const pick = (i, productId) => {
    const p = byId[productId];
    update(i, { productId, price: p ? String(p[priceField] ?? p.price ?? '') : '' });
  };

  return (
    <div className="lines">
      <div className="lines-head">
        <span>{t.ops.product}</span>
        <span>{t.ops.qty}</span>
        <span>{priceLabel}</span>
        <span className="num">{t.ops.lineTotal}</span>
        <span />
      </div>
      {lines.map((l, i) => {
        const total = (Number(l.quantity) || 0) * (Number(l.price) || 0);
        return (
          <div className="lines-row" key={i}>
            <ProductSelect products={products} value={l.productId} onChange={(v) => pick(i, v)} required />
            <input type="number" min="1" step="1" dir="ltr" required value={l.quantity}
              onChange={(e) => update(i, { quantity: e.target.value })} aria-label={t.ops.qty} />
            <input type="number" min="0" step="0.01" dir="ltr" required value={l.price}
              onChange={(e) => update(i, { price: e.target.value })} aria-label={priceLabel} />
            <span className="num">{formatPrice(total, lang)}</span>
            <button type="button" className="link-btn danger" disabled={lines.length === 1}
              onClick={() => setLines(lines.filter((_, idx) => idx !== i))}>
              {t.ops.removeLine}
            </button>
          </div>
        );
      })}
      <button type="button" className="btn btn-sm btn-outline"
        onClick={() => setLines([...lines, { productId: '', quantity: 1, price: '' }])}>
        {t.ops.addLine}
      </button>
    </div>
  );
}

export const linesTotal = (lines) =>
  lines.reduce((s, l) => s + (Number(l.quantity) || 0) * (Number(l.price) || 0), 0);

/* ------------------------------------------------------------------ payment form */

/**
 * Record a payment received from a client (kind="client") or made to a provider (kind="provider").
 * links: optional [{id, label}] of orders / purchases the payment can be tied to.
 */
export function PaymentForm({ open, onClose, kind, partyId, partyName, links = [], defaultAmount, onSaved }) {
  const { t } = useLang();
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm({ amount: defaultAmount > 0 ? Number(defaultAmount).toFixed(2) : '', paidAt: todayIso(),
        method: t.ops.paymentMethods[0], linkId: '', notes: '' });
      setError('');
    }
  }, [open, defaultAmount, t.ops.paymentMethods]);

  if (!open || !form) return null;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body = { amount: Number(form.amount), paidAt: form.paidAt, method: form.method, notes: form.notes };
      if (kind === 'client') {
        await api.post('/admin/clients/payments', { ...body, clientId: partyId, orderId: form.linkId ? Number(form.linkId) : null });
      } else {
        await api.post('/admin/suppliers/payments', { ...body, supplierId: partyId, purchaseId: form.linkId ? Number(form.linkId) : null });
      }
      onSaved?.();
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose}
      title={`${kind === 'client' ? t.ops.paymentReceived : t.ops.paymentMade} · ${partyName || ''}`}>
      <form onSubmit={submit}>
        <div className="grid-2">
          <label className="field">
            <span>{t.ops.amount} ({t.ops.egp})</span>
            <input type="number" min="0.01" step="0.01" dir="ltr" required autoFocus value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value })} />
          </label>
          <label className="field">
            <span>{t.ops.date}</span>
            <input type="date" required value={form.paidAt} onChange={(e) => setForm({ ...form, paidAt: e.target.value })} />
          </label>
          <label className="field">
            <span>{t.ops.method}</span>
            <input list="payment-methods" value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value })} />
            <datalist id="payment-methods">
              {t.ops.paymentMethods.map((m) => <option key={m} value={m} />)}
            </datalist>
          </label>
          {links.length > 0 && (
            <label className="field">
              <span>{kind === 'client' ? t.ops.forOrder : t.ops.forPurchase}</span>
              <select value={form.linkId} onChange={(e) => setForm({ ...form, linkId: e.target.value })}>
                <option value="">{t.ops.general}</option>
                {links.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
              </select>
            </label>
          )}
        </div>
        <label className="field">
          <span>{t.ops.notes}</span>
          <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
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

/* ------------------------------------------------------------------ sale form */

const SALE_PAYMENT_METHODS = ['CREDIT', 'CASH_ON_DELIVERY', 'POS_ON_DELIVERY', 'BANK_TRANSFER'];

/** Staff-entered sale for a client (phone / WhatsApp order). */
export function SaleForm({ open, onClose, clientId: fixedClientId, onSaved }) {
  const { t, lang } = useLang();
  const { products, reload: reloadProducts } = useStockProducts();
  const { clients } = useClients();
  const [form, setForm] = useState(null);
  const [lines, setLines] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm({ clientId: fixedClientId ? String(fixedClientId) : '', deliveryMethod: 'HOME_DELIVERY',
        deliveryAddress: '', paymentMethod: 'CREDIT', amountPaidNow: '', paymentNote: '', notes: '' });
      setLines([{ productId: '', quantity: 1, price: '' }]);
      setError('');
      reloadProducts();
    }
  }, [open, fixedClientId, reloadProducts]);

  if (!open || !form) return null;
  const total = linesTotal(lines);
  const client = clients.find((c) => String(c.id) === form.clientId);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const r = await api.post('/admin/clients/sales', {
        clientId: Number(form.clientId),
        items: lines.map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity), unitPrice: Number(l.price) })),
        paymentMethod: form.paymentMethod,
        deliveryMethod: form.deliveryMethod,
        deliveryAddress: form.deliveryAddress,
        notes: form.notes,
        amountPaidNow: form.amountPaidNow ? Number(form.amountPaidNow) : 0,
        paymentNote: form.paymentNote,
      });
      onSaved?.(r.data);
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} wide onClose={onClose} title={t.ops.saleTitle}>
      <form onSubmit={submit}>
        <p className="small muted">{t.ops.saleHint}</p>
        <label className="field">
          <span>{t.ops.client}</span>
          <select required value={form.clientId} disabled={!!fixedClientId}
            onChange={(e) => setForm({ ...form, clientId: e.target.value })}>
            <option value="">{t.ops.chooseClient}</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.name}{c.city ? ` · ${c.city}` : ''}</option>)}
          </select>
        </label>
        <LineItems lines={lines} setLines={setLines} products={products} priceLabel={t.ops.unitPrice} priceField="price" />
        <div className="grid-3">
          <label className="field">
            <span>{t.ops.deliveryMethod}</span>
            <select value={form.deliveryMethod} onChange={(e) => setForm({ ...form, deliveryMethod: e.target.value })}>
              <option value="HOME_DELIVERY">{t.deliveryMethod.HOME_DELIVERY}</option>
              <option value="SHOP_PICKUP">{t.deliveryMethod.SHOP_PICKUP}</option>
            </select>
          </label>
          <label className="field">
            <span>{t.ops.paymentMethod}</span>
            <select value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })}>
              {SALE_PAYMENT_METHODS.map((m) => <option key={m} value={m}>{t.paymentMethod[m]}</option>)}
            </select>
          </label>
          <label className="field">
            <span>{t.ops.paidNow} ({t.ops.egp})</span>
            <input type="number" min="0" step="0.01" dir="ltr" value={form.amountPaidNow} placeholder="0"
              onChange={(e) => setForm({ ...form, amountPaidNow: e.target.value })} />
            <small className="muted">{t.ops.paidNowHint}</small>
          </label>
        </div>
        {form.deliveryMethod === 'HOME_DELIVERY' && (
          <label className="field">
            <span>{t.checkout.address}</span>
            <input value={form.deliveryAddress}
              placeholder={client ? [client.address, client.city].filter(Boolean).join(', ') : ''}
              onChange={(e) => setForm({ ...form, deliveryAddress: e.target.value })} />
          </label>
        )}
        <label className="field">
          <span>{t.ops.notes}</span>
          <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        </label>
        <div className="summary-row total"><span>{t.common.total}</span><strong>{formatPrice(total, lang)}</strong></div>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}
