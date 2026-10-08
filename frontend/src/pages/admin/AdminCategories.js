import { useCallback, useEffect, useState } from 'react';
import api, { errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';
import Modal from '../../components/Modal';
import ImageField from './ImageField';
import { imgFallback, localized, PLACEHOLDER_IMG } from '../../utils/format';

const EMPTY = { name: '', nameAr: '', description: '', descriptionAr: '', imageUrl: '' };

export default function AdminCategories() {
  const { t, lang } = useLang();
  const [categories, setCategories] = useState([]);
  const [counts, setCounts] = useState({});
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [listError, setListError] = useState('');

  const load = useCallback(() => {
    Promise.all([api.get('/categories'), api.get('/products')])
      .then(([c, p]) => {
        setCategories(c.data);
        const m = {};
        p.data.forEach((x) => { if (x.category) m[x.category.id] = (m[x.category.id] || 0) + 1; });
        setCounts(m);
      })
      .catch(() => setListError(t.common.error));
  }, [t.common.error]);

  useEffect(load, [load]);

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      if (editing.id) await api.put(`/categories/${editing.id}`, editing);
      else await api.post('/categories', editing);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (c) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    setListError('');
    try {
      await api.delete(`/categories/${c.id}`);
      load();
    } catch (err) {
      setListError(errorMessage(err, t.common.error));
    }
  };

  const set = (k) => (e) => setEditing({ ...editing, [k]: e.target.value });

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.admin.categories}</h1>
        <button className="btn btn-primary" onClick={() => { setError(''); setEditing({ ...EMPTY }); }}>+ {t.admin.newCategory}</button>
      </div>
      {listError && <div className="alert alert-error">{listError}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.admin.image}</th>
              <th>{t.admin.nameEn}</th>
              <th>{t.admin.nameAr}</th>
              <th className="num">{t.admin.productsCount}</th>
              <th className="end">{t.common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((c) => (
              <tr key={c.id}>
                <td><img className="thumb" src={c.imageUrl || PLACEHOLDER_IMG} alt="" onError={imgFallback} /></td>
                <td>
                  <strong>{c.name}</strong>
                  <div className="small muted clamp">{c.description}</div>
                </td>
                <td dir="rtl" lang="ar">
                  {c.nameAr}
                  <div className="small muted clamp">{c.descriptionAr}</div>
                </td>
                <td className="num">{counts[c.id] || 0}</td>
                <td className="end nowrap">
                  <button className="link-btn" onClick={() => { setError(''); setEditing({ ...EMPTY, ...c }); }}>{t.common.edit}</button>
                  <button className="link-btn danger" onClick={() => remove(c)}>{t.common.delete}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {categories.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
      </div>

      <Modal open={!!editing} wide title={editing?.id ? `${t.admin.editCategory}: ${localized(editing, 'name', lang)}` : t.admin.newCategory} onClose={() => setEditing(null)}>
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
