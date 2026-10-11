import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatDate, formatPrice, imgFallback, localized, PLACEHOLDER_IMG } from '../../../utils/format';
import { StatTile } from './shared';

function MovementTable({ movements, showProduct }) {
  const { t, lang } = useLang();
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>{t.ops.date}</th>
            {showProduct && <th>{t.ops.product}</th>}
            <th>{t.ops.type}</th>
            <th className="num">{t.ops.change}</th>
            <th className="num">{t.ops.after}</th>
            <th>{t.ops.reference}</th>
            <th>{t.ops.by}</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((m) => (
            <tr key={m.id}>
              <td className="nowrap">{formatDate(m.createdAt, lang, true)}</td>
              {showProduct && <td>{m.product?.sku ? <span className="muted small">{m.product.sku} · </span> : null}{localized(m.product, 'name', lang)}</td>}
              <td>{t.ops.movementType[m.type] || m.type}</td>
              <td className={`num ${m.quantityChange > 0 ? 'pos' : 'neg'}`} dir="ltr">
                {m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange}
              </td>
              <td className="num">{m.stockAfter}</td>
              <td className="small">{[m.reference, m.note].filter(Boolean).join(' · ')}</td>
              <td className="small muted">{m.createdBy || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {movements.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
    </div>
  );
}

function HistoryModal({ product, onClose }) {
  const { t, lang } = useLang();
  const [movements, setMovements] = useState(null);
  useEffect(() => {
    api.get('/admin/stock/movements', { params: product ? { productId: product.id, limit: 300 } : { limit: 300 } })
      .then((r) => setMovements(r.data)).catch(() => setMovements([]));
  }, [product]);
  return (
    <Modal open wide onClose={onClose} title={product ? `${t.ops.history} · ${localized(product, 'name', lang)}` : t.ops.allHistory}>
      {movements == null ? <p className="muted">{t.common.loading}</p> : <MovementTable movements={movements} showProduct={!product} />}
    </Modal>
  );
}

function AdjustModal({ product, mode, onClose, onSaved }) {
  const { t, lang } = useLang();
  const [kind, setKind] = useState('ADD'); // ADD | REMOVE | DAMAGE
  const [qty, setQty] = useState(mode === 'count' ? String(product.stockQuantity) : '1');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (mode === 'count') {
        await api.post('/admin/stock/count', { productId: product.id, countedQuantity: Number(qty), note });
      } else {
        const n = Math.abs(Number(qty));
        await api.post('/admin/stock/adjust', {
          productId: product.id,
          quantityChange: kind === 'ADD' ? n : -n,
          type: kind === 'DAMAGE' ? 'DAMAGE' : 'ADJUSTMENT',
          note,
        });
      }
      onSaved();
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`${mode === 'count' ? t.ops.countTitle : t.ops.adjustTitle} · ${localized(product, 'name', lang)}`}>
      <form onSubmit={submit}>
        <p className="small muted">{t.ops.inStock}: <strong>{product.stockQuantity}</strong></p>
        {mode === 'count' ? (
          <>
            <label className="field">
              <span>{t.ops.countedQty}</span>
              <input type="number" min="0" step="1" dir="ltr" required autoFocus value={qty} onChange={(e) => setQty(e.target.value)} />
            </label>
            <p className="small muted">{t.ops.countHint}</p>
          </>
        ) : (
          <>
            <div className="segmented" role="radiogroup">
              {[['ADD', t.ops.addStock], ['REMOVE', t.ops.removeStock], ['DAMAGE', t.ops.damage]].map(([k, label]) => (
                <button type="button" key={k} role="radio" aria-checked={kind === k}
                  className={kind === k ? 'active' : ''} onClick={() => setKind(k)}>{label}</button>
              ))}
            </div>
            <label className="field">
              <span>{t.ops.qty}</span>
              <input type="number" min="1" step="1" dir="ltr" required autoFocus value={qty} onChange={(e) => setQty(e.target.value)} />
            </label>
          </>
        )}
        <label className="field">
          <span>{t.ops.notes}</span>
          <input value={note} onChange={(e) => setNote(e.target.value)} />
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

export default function AdminStock() {
  const { t, lang } = useLang();
  const [rows, setRows] = useState([]);
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [lowOnly, setLowOnly] = useState(false);
  const [adjusting, setAdjusting] = useState(null); // {product, mode}
  const [history, setHistory] = useState(undefined); // undefined = closed, null = all, product = one
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    api.get('/admin/stock').then((r) => setRows(r.data))
      .catch(() => setError(t.common.error)).finally(() => setLoading(false));
  }, [t.common.error]);
  useEffect(load, [load]);

  const categories = useMemo(() => {
    const m = new Map();
    rows.forEach((r) => r.categoryId && m.set(r.categoryId, { id: r.categoryId, name: r.categoryName, nameAr: r.categoryNameAr }));
    return [...m.values()];
  }, [rows]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return rows.filter((r) => {
      if (lowOnly && r.stockStatus === 'OK') return false;
      if (category && String(r.categoryId) !== category) return false;
      if (!term) return true;
      return [r.name, r.nameAr, r.sku].some((v) => (v || '').toLowerCase().includes(term));
    });
  }, [rows, q, category, lowOnly]);

  const totalValue = rows.reduce((s, r) => s + Number(r.stockValue || 0), 0);
  const low = rows.filter((r) => r.stockStatus === 'LOW').length;
  const out = rows.filter((r) => r.stockStatus === 'OUT').length;

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.stock}</h1>
        <button className="btn btn-outline" onClick={() => setHistory(null)}>{t.ops.allHistory}</button>
      </div>

      <div className="stat-grid">
        <StatTile label={t.ops.stockValue} value={formatPrice(totalValue, lang)} hint={`${rows.length} ${t.admin.products}`} />
        <StatTile label={t.ops.inStock} value={rows.reduce((s, r) => s + Math.max(0, r.stockQuantity), 0)} />
        <StatTile label={t.ops.lowCount} value={low} tone={low ? 'warn' : undefined} />
        <StatTile label={t.ops.outCount} value={out} tone={out ? 'bad' : undefined} />
      </div>

      <div className="toolbar">
        <input type="search" placeholder={t.ops.search} value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">{t.products.allCategories}</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{localized(c, 'name', lang)}</option>)}
        </select>
        <label className="check inline">
          <input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />
          <span>{t.ops.lowOnly}</span>
        </label>
      </div>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.ops.product}</th>
              <th className="num">{t.ops.inStock}</th>
              <th className="num">{t.ops.reorderLevel}</th>
              <th className="num">{t.ops.onOrder}</th>
              <th className="num">{t.ops.costPrice}</th>
              <th className="num">{t.ops.sellPrice}</th>
              <th className="num">{t.ops.margin}</th>
              <th className="num">{t.ops.stockValue}</th>
              <th className="end">{t.common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.id}>
                <td>
                  <div className="product-cell">
                    <img className="thumb thumb-sm" src={r.imageUrl || PLACEHOLDER_IMG} alt="" onError={imgFallback} />
                    <div>
                      <strong>{localized(r, 'name', lang)}</strong>
                      <div className="small muted">{[r.sku, localized({ name: r.categoryName, nameAr: r.categoryNameAr }, 'name', lang)].filter(Boolean).join(' · ')}</div>
                    </div>
                  </div>
                </td>
                <td className="num">
                  <span className={`pill ${r.stockStatus === 'OUT' ? 'pill-bad' : r.stockStatus === 'LOW' ? 'pill-warn' : 'pill-muted'}`}>{r.stockQuantity}</span>
                </td>
                <td className="num">{r.reorderLevel}</td>
                <td className="num">{r.onOrder || '—'}</td>
                <td className="num">{r.costPrice != null ? formatPrice(r.costPrice, lang) : '—'}</td>
                <td className="num">{formatPrice(r.price, lang)}</td>
                <td className="num">{r.marginPercent != null ? `${r.marginPercent}%` : '—'}</td>
                <td className="num">{formatPrice(r.stockValue, lang)}</td>
                <td className="end nowrap">
                  <button className="link-btn" onClick={() => setAdjusting({ product: r, mode: 'adjust' })}>{t.ops.adjust}</button>
                  <button className="link-btn" onClick={() => setAdjusting({ product: r, mode: 'count' })}>{t.ops.count}</button>
                  <button className="link-btn" onClick={() => setHistory(r)}>{t.ops.history}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
      </div>

      {adjusting && <AdjustModal product={adjusting.product} mode={adjusting.mode} onClose={() => setAdjusting(null)} onSaved={load} />}
      {history !== undefined && <HistoryModal product={history} onClose={() => setHistory(undefined)} />}
    </div>
  );
}
