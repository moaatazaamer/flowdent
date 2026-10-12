import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import Logo from './Logo';

export default function Navbar() {
  const { t, switchLang } = useLang();
  const { user, isAuthenticated, isStaff, logout } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [location.pathname]);

  const submit = (e) => {
    e.preventDefault();
    const term = q.trim();
    if (term) navigate(`/search?q=${encodeURIComponent(term)}`);
  };

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand" aria-label={t.brand}>
          <Logo size={42} />
        </Link>

        <form className="nav-search" onSubmit={submit} role="search">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t.nav.searchPlaceholder}
            aria-label={t.nav.search}
          />
          <button type="submit" className="icon-btn" aria-label={t.nav.search}>
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" strokeWidth="2" />
              <path d="M20 20l-4-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
        </form>

        <button className="nav-toggle icon-btn" onClick={() => setOpen((o) => !o)} aria-label={t.nav.menu} aria-expanded={open}>
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
            <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>

        <nav className={`nav-links ${open ? 'open' : ''}`}>
          <NavLink to="/" end>{t.nav.home}</NavLink>
          <NavLink to="/products">{t.nav.products}</NavLink>
          <NavLink to="/ora" className="nav-ora">{t.nav.ora}</NavLink>
          {isAuthenticated && <NavLink to="/orders">{t.nav.orders}</NavLink>}
          {isAuthenticated && <NavLink to="/profile">{t.nav.profile}</NavLink>}
          {isStaff && <NavLink to="/admin" className="nav-admin">{t.nav.admin}</NavLink>}
          {isAuthenticated ? (
            <button className="link-btn" onClick={() => { logout(); navigate('/'); }} title={user?.email}>
              {t.nav.logout}
            </button>
          ) : (
            <>
              <NavLink to="/login">{t.nav.login}</NavLink>
              <NavLink to="/register" className="btn btn-sm btn-outline">{t.nav.register}</NavLink>
            </>
          )}
          <button className="lang-switch" onClick={() => switchLang()} aria-label="Switch language">
            {t.switchTo}
          </button>
        </nav>

        <Link to="/cart" className="cart-link" aria-label={t.nav.cart}>
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
            <path d="M3 4h2l2.4 11.2a2 2 0 002 1.6h7.7a2 2 0 002-1.5L21 8H6.2" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            <circle cx="10" cy="20" r="1.4" fill="currentColor" />
            <circle cx="17" cy="20" r="1.4" fill="currentColor" />
          </svg>
          {count > 0 && <span className="badge">{count}</span>}
        </Link>
      </div>
    </header>
  );
}
