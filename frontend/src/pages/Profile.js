import { useCallback, useEffect, useState } from 'react';
import api, { errorMessage } from '../api';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';

const EMPTY_ADDRESS = { label: '', fullAddress: '', city: '', postalCode: '', isDefault: false };

export default function Profile() {
  const { t } = useLang();
  const { user, updateUser } = useAuth();
  const [form, setForm] = useState({
    fullName: user?.fullName || '',
    phone: user?.phone || '',
    address: user?.address || '',
    currentPassword: '',
    newPassword: '',
  });
  const [msg, setMsg] = useState(null);
  const [saving, setSaving] = useState(false);

  const [addresses, setAddresses] = useState([]);
  const [editing, setEditing] = useState(null); // null | address object (id undefined = new)
  const [addrError, setAddrError] = useState('');

  const loadAddresses = useCallback(() => {
    api.get('/profile/addresses').then((r) => setAddresses(r.data)).catch(() => {});
  }, []);

  useEffect(loadAddresses, [loadAddresses]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const saveProfile = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMsg(null);
    try {
      const payload = { fullName: form.fullName, phone: form.phone, address: form.address };
      if (form.newPassword) {
        payload.currentPassword = form.currentPassword;
        payload.newPassword = form.newPassword;
      }
      const r = await api.put('/profile', payload);
      updateUser(r.data);
      setForm((f) => ({ ...f, currentPassword: '', newPassword: '' }));
      setMsg({ ok: true, text: t.profile.updated });
    } catch (err) {
      setMsg({ ok: false, text: errorMessage(err, t.common.error) });
    } finally {
      setSaving(false);
    }
  };

  const saveAddress = async (e) => {
    e.preventDefault();
    setAddrError('');
    try {
      if (editing.id) await api.put(`/profile/addresses/${editing.id}`, editing);
      else await api.post('/profile/addresses', editing);
      setEditing(null);
      loadAddresses();
    } catch (err) {
      setAddrError(errorMessage(err, t.common.error));
    }
  };

  const removeAddress = async (a) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    await api.delete(`/profile/addresses/${a.id}`).catch(() => {});
    loadAddresses();
  };

  const makeDefault = async (a) => {
    await api.put(`/profile/addresses/${a.id}`, { ...a, isDefault: true }).catch(() => {});
    loadAddresses();
  };

  return (
    <div className="container page">
      <h1>{t.profile.title}</h1>
      <div className="profile-layout">
        <form className="card" onSubmit={saveProfile}>
          <h3>{t.profile.details}</h3>
          <label className="field">
            <span>{t.auth.email}</span>
            <input value={user?.email || ''} disabled dir="ltr" />
          </label>
          <label className="field">
            <span>{t.auth.fullName}</span>
            <input value={form.fullName} onChange={set('fullName')} />
          </label>
          <label className="field">
            <span>{t.auth.phone}</span>
            <input type="tel" dir="ltr" value={form.phone} onChange={set('phone')} />
          </label>
          <label className="field">
            <span>{t.auth.address}</span>
            <input value={form.address} onChange={set('address')} />
          </label>
          <h4 className="sub-heading">{t.profile.changePassword}</h4>
          <div className="grid-2">
            <label className="field">
              <span>{t.profile.currentPassword}</span>
              <input type="password" dir="ltr" autoComplete="current-password" value={form.currentPassword} onChange={set('currentPassword')} />
            </label>
            <label className="field">
              <span>{t.profile.newPassword}</span>
              <input type="password" dir="ltr" minLength={6} autoComplete="new-password" value={form.newPassword} onChange={set('newPassword')} />
            </label>
          </div>
          {msg && <div className={`alert ${msg.ok ? 'alert-ok' : 'alert-error'}`}>{msg.text}</div>}
          <button className="btn btn-primary" disabled={saving}>{saving ? t.common.saving : t.common.save}</button>
        </form>

        <section className="card">
          <div className="section-head">
            <h3>{t.profile.addresses}</h3>
            <button className="btn btn-sm btn-outline" onClick={() => { setAddrError(''); setEditing({ ...EMPTY_ADDRESS }); }}>
              + {t.profile.addAddress}
            </button>
          </div>
          {addresses.length === 0 ? (
            <p className="muted">{t.profile.noAddresses}</p>
          ) : (
            <ul className="address-list">
              {addresses.map((a) => (
                <li key={a.id} className={`address-item ${a.isDefault ? 'is-default' : ''}`}>
                  <div>
                    <strong>{a.label || t.profile.fullAddress}</strong>
                    {a.isDefault && <span className="pill pill-ok">{t.profile.isDefault}</span>}
                    <div className="small">{[a.fullAddress, a.city, a.postalCode].filter(Boolean).join(', ')}</div>
                  </div>
                  <div className="row-actions">
                    {!a.isDefault && <button className="link-btn" onClick={() => makeDefault(a)}>{t.profile.makeDefault}</button>}
                    <button className="link-btn" onClick={() => { setAddrError(''); setEditing({ ...a }); }}>{t.common.edit}</button>
                    <button className="link-btn danger" onClick={() => removeAddress(a)}>{t.common.delete}</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <Modal
        open={!!editing}
        title={editing?.id ? t.profile.editAddress : t.profile.addAddress}
        onClose={() => setEditing(null)}
      >
        {editing && (
          <form onSubmit={saveAddress}>
            <label className="field">
              <span>{t.profile.label}</span>
              <input placeholder={t.profile.labelPlaceholder} value={editing.label || ''} onChange={(e) => setEditing({ ...editing, label: e.target.value })} />
            </label>
            <label className="field">
              <span>{t.profile.fullAddress}</span>
              <textarea rows="2" required value={editing.fullAddress || ''} onChange={(e) => setEditing({ ...editing, fullAddress: e.target.value })} />
            </label>
            <div className="grid-2">
              <label className="field">
                <span>{t.profile.city}</span>
                <input value={editing.city || ''} onChange={(e) => setEditing({ ...editing, city: e.target.value })} />
              </label>
              <label className="field">
                <span>{t.profile.postalCode}</span>
                <input dir="ltr" value={editing.postalCode || ''} onChange={(e) => setEditing({ ...editing, postalCode: e.target.value })} />
              </label>
            </div>
            <label className="check">
              <input type="checkbox" checked={!!editing.isDefault} onChange={(e) => setEditing({ ...editing, isDefault: e.target.checked })} />
              <span>{t.profile.isDefault}</span>
            </label>
            {addrError && <div className="alert alert-error">{addrError}</div>}
            <div className="row-actions end">
              <button type="button" className="btn btn-outline" onClick={() => setEditing(null)}>{t.common.cancel}</button>
              <button className="btn btn-primary">{t.common.save}</button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
