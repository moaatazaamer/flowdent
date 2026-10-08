import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { t } = useLang();
  const { login, isAuthenticated, isStaff } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (isAuthenticated) return <Navigate to={isStaff ? '/admin' : '/'} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const u = await login(email.trim(), password);
      navigate(u.role === 'ADMIN' || u.role === 'ORDER_ADMIN' ? '/admin' : '/', { replace: true });
    } catch (err) {
      setError(err?.response?.status === 401 ? t.auth.invalid : errorMessage(err, t.common.error));
      setBusy(false);
    }
  };

  return (
    <div className="container page auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>{t.auth.loginTitle}</h1>
        <p className="muted">{t.auth.loginSubtitle}</p>
        <label className="field">
          <span>{t.auth.emailOrUser}</span>
          <input required dir="ltr" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>{t.auth.password}</span>
          <input required type="password" dir="ltr" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="alert alert-error">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? t.auth.signingIn : t.auth.signIn}</button>
        <p className="small center">
          {t.auth.noAccount} <Link to="/register" className="link">{t.auth.signUp}</Link>
        </p>
      </form>
    </div>
  );
}
