import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';
import Modal from '../../components/Modal';
import ImageField from './ImageField';
import { formatPrice, imgFallback, localized, PLACEHOLDER_IMG } from '../../utils/format';

const EMPTY = {
  name: '', nameAr: '', description: '', descriptionAr: '',
  price: '', stockQuantity: 0, imageUrl: '', categoryId: '',
  sku: '', costPrice: '', reorderLevel: 10, brand: '',
};

export default function AdminProducts() {
  const { t, lang } = useLang();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [filter, setFilter] = useState('');
  const [catFilter, setCatFilter] = useState('');
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [listError, setListError] = useState('');

  const load = useCallback(() => {
    Promise.all([api.get('/products'), api.get('/categories'), api.get('/admin/stock')])
      .then(([p, c, s]) => {
        // Cost and reorder level are internal and only come from the admin stock API.
        const internal = Object.fromEntries(s.data.map((x) => [x.id, x]));
        setProducts(p.data.map((x) => ({
          ...x,
          costPrice: internal[x.id]?.costPrice ?? null,
          reorderLevel: internal[x.id]?.reorderLevel ?? 10,
          marginPercent: internal[x.id]?.marginPercent ?? null,
        })));
        setCategories(c.data);
      })
      .catch(() => setListError(t.common.error));
  }, [t.common.error]);

  useEffect(load, [load]);

  const visible = useMemo(() => {
    const f = filter.trim().toLowerCase();
    return products.filter((p) => {
      if (catFilter && String(p.category?.id) !== catFilter) return false;
      if (!f) return true;
      return (p.name || '').toLowerCase().includes(f) || (p.nameAr || '').includes(filter.trim())
        || (p.sku || '').toLowerCase().includes(f);
    });
  }, [products, filter, catFilter]);

  const openEdit = (p) => {
    setError('');
    setEditing(
      p
        ? { ...EMPTY, ...p, sku: p.sku || '', brand: p.brand || '', costPrice: p.costPrice ?? '',
            categoryId: p.category?.id ? String(p.category.id) : '' }
        : { ...EMPTY, categoryId: catFilter }
    );
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    const payload = {
      name: editing.name,
      nameAr: editing.nameAr,
      description: editing.description,
      descriptionAr: editing.descriptionAr,
      price: editing.price === '' ? null : Number(editing.price),
      stockQuantity: Number(editing.stockQuantity) || 0,
      imageUrl: editing.imageUrl,
      categoryId: editing.categoryId ? Number(editing.categoryId) : null,
      sku: editing.sku || '',
      brand: editing.brand || '',
      costPrice: editing.costPrice === '' || editing.costPrice == null ? null : Number(editing.costPrice),
      reorderLevel: editing.reorderLevel === '' ? null : Number(editing.reorderLevel),
    };
    try {
      if (editing.id) await api.put(`/products/${editing.id}`, payload);
      else await api.post('/products', payload);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (p) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    setListError('');
    try {
      await api.delete(`/products/${p.id}`);
      load();
    } catch (err) {
      setListError(errorMessage(err, t.common.error));
    }
  };

  const set = (k) => (e) => setEditing({ ...editing, [k]: e.target.value });

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.admin.products}</h1>
        <button className="btn btn-primary" onClick={() => openEdit(null)}>+ {t.admin.newProduct}</button>
      </div>
      <div className="toolbar">
        <input type="search" placeholder={t.admin.filter} value={filter} onChange={(e) => setFilter(e.target.value)} />
        <select value={catFilter} onChange={(e) => setCatFilter(e.target.value)}>
          <option value="">{t.products.allCategories}</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{localized(c, 'name', lang)}</option>)}
        </select>
        <span className="muted small">{t.products.count(visible.length)}</span>
      </div>
      {listError && <div className="alert alert-error">{listError}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.admin.image}</th>
              <th>{t.admin.name}</th>
              <th>{t.admin.category}</th>
              <th className="num">{t.ops.costPrice}</th>
              <th className="num">{t.common.price}</th>
              <th className="num">{t.ops.margin}</th>
              <th className="num">{t.admin.stock}</th>
              <th className="end">{t.common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.id}>
                <td><img className="thumb" src={p.imageUrl || PLACEHOLDER_IMG} alt="" onError={imgFallback} /></td>
                <td>
                  <strong>{p.name}</strong>
                  <div className="small muted" dir="rtl" lang="ar">{p.nameAr}</div>
                  {(p.sku || p.brand) && <div className="small muted">{[p.sku, p.brand].filter(Boolean).join(' · ')}</div>}
                </td>
                <td>{p.category ? localized(p.category, 'name', lang) : '—'}</td>
                <td className="num">{p.costPrice != null ? formatPrice(p.costPrice, lang) : '—'}</td>
                <td className="num">{formatPrice(p.price, lang)}</td>
                <td className="num">{p.marginPercent != null ? `${p.marginPercent}%` : '—'}</td>
                <td className="num">
                  <span className={`pill ${p.stockQuantity === 0 ? 'pill-bad' : p.stockQuantity <= p.reorderLevel ? 'pill-warn' : 'pill-muted'}`}>{p.stockQuantity}</span>
                </td>
                <td className="end nowrap">
                  <button className="link-btn" onClick={() => openEdit(p)}>{t.common.edit}</button>
                  <button className="link-btn danger" onClick={() => remove(p)}>{t.common.delete}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {visible.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
      </div>

      <Modal open={!!editing} wide title={editing?.id ? t.admin.editProduct : t.admin.newProduct} onClose={() => setEditing(null)}>
        {editing && (
          <form onSubmit={save}>
            <div className="grid-2">
              <label className="field">
                <span>{t.admin.nameEn}</span>
                <input required dir="ltr" value={editing.name} onChange={set('name')} />
              </label>
              <label className="field">
                <span>{t.admin.nameAr}</span>
                <input dir="rtl" lang="ar" value={editing.nameAr || ''} onChange={set('nameAr')} />
              </label>
              <label className="field">
                <span>{t.admin.descriptionEn}</span>
                <textarea rows="3" dir="ltr" value={editing.description || ''} onChange={set('description')} />
              </label>
              <label className="field">
                <span>{t.admin.descriptionAr}</span>
                <textarea rows="3" dir="rtl" lang="ar" value={editing.descriptionAr || ''} onChange={set('descriptionAr')} />
              </label>
            </div>
            <div className="grid-3">
              <label className="field">
                <span>{t.ops.sellPrice} ({t.ops.egp})</span>
                <input required type="number" min="0" step="0.01" dir="ltr" value={editing.price} onChange={set('price')} />
              </label>
              <label className="field">
                <span>{t.ops.costPrice} ({t.ops.egp})</span>
                <input type="number" min="0" step="0.01" dir="ltr" value={editing.costPrice} onChange={set('costPrice')} />
              </label>
              <label className="field">
                <span>{t.ops.sku}</span>
                <input dir="ltr" value={editing.sku} onChange={set('sku')} />
              </label>
              <label className="field">
                <span>{t.admin.brand}</span>
                <input dir="ltr" list="product-brands" placeholder={t.admin.brandHint} value={editing.brand} onChange={set('brand')} />
                <datalist id="product-brands">
                  {[...new Set(['Ora', ...products.map((x) => x.brand).filter(Boolean)])].map((b) => <option key={b} value={b} />)}
                </datalist>
              </label>
              <label className="field">
                <span>{t.admin.stock}</span>
                <input required type="number" min="0" step="1" dir="ltr" value={editing.stockQuantity} onChange={set('stockQuantity')} />
              </label>
              <label className="field">
                <span>{t.ops.reorderLevel}</span>
                <input type="number" min="0" step="1" dir="ltr" value={editing.reorderLevel} onChange={set('reorderLevel')} />
              </label>
              <label className="field">
                <span>{t.admin.category}</span>
                <select value={editing.categoryId} onChange={set('categoryId')}>
                  <option value="">{t.admin.noCategory}</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{localized(c, 'name', lang)}</option>)}
                </select>
              </label>
            </div>
            <ImageField value={editing.imageUrl || ''} onChange={(url) => setEditing({ ...editing, imageUrl: url })} />
            {error && <div className="alert alert-error">{error}</div>}
            <div className="row-actions end">
              <button type="button" className="btn btn-outline" onClick={() => setEditing(null)}>{t.common.cancel}</button>
              <button className="btn btn-primary" disabled={saving}>{saving ? t.common.saving : t.common.save}</button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
