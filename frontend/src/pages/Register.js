import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { t } = useLang();
  const { register, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '', address: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (isAuthenticated) return <Navigate to="/" replace />;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await register({ ...form, email: form.email.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, t.common.error));
      setBusy(false);
    }
  };

  return (
    <div className="container page auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>{t.auth.registerTitle}</h1>
        <p className="muted">{t.auth.registerSubtitle}</p>
        <label className="field">
          <span>{t.auth.fullName}</span>
          <input required autoComplete="name" value={form.fullName} onChange={set('fullName')} />
        </label>
        <label className="field">
          <span>{t.auth.email}</span>
          <input required type="email" dir="ltr" autoComplete="email" value={form.email} onChange={set('email')} />
        </label>
        <label className="field">
          <span>{t.auth.password}</span>
          <input required type="password" dir="ltr" minLength={6} autoComplete="new-password" value={form.password} onChange={set('password')} />
          <small className="muted">{t.auth.passwordMin}</small>
        </label>
        <label className="field">
          <span>{t.auth.phone} <span className="muted">({t.common.optional})</span></span>
          <input type="tel" dir="ltr" autoComplete="tel" value={form.phone} onChange={set('phone')} />
        </label>
        <label className="field">
          <span>{t.auth.address} <span className="muted">({t.common.optional})</span></span>
          <input autoComplete="street-address" value={form.address} onChange={set('address')} />
        </label>
        {error && <div className="alert alert-error">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? t.auth.signingUp : t.auth.signUp}</button>
        <p className="small center">
          {t.auth.haveAccount} <Link to="/login" className="link">{t.auth.signIn}</Link>
        </p>
      </form>
    </div>
  );
}
