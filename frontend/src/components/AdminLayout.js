import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';
import Logo from './Logo';

const ICONS = {
  dashboard: 'M4 13h6V4H4v9zm0 7h6v-5H4v5zm10 0h6v-9h-6v9zm0-16v5h6V4h-6z',
  categories: 'M4 5h7v6H4zM13 5h7v6h-7zM4 13h7v6H4zM13 13h7v6h-7z',
  products: 'M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3zm0 2.3L6.2 8.5 12 11.7l5.8-3.2L12 5.3z',
  orders: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3zm3 5h6M9 12h6',
  customers: 'M12 12a4 4 0 100-8 4 4 0 000 8zm-7 9c0-4 3-6 7-6s7 2 7 6',
  import: 'M12 3v12m0 0l-4-4m4 4l4-4M4 17v3h16v-3',
};

function Icon({ name }) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path d={ICONS[name]} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export default function AdminLayout() {
  const { t, switchLang } = useLang();
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const links = [
    { to: '/admin', label: t.admin.dashboard, icon: 'dashboard', end: true, show: true },
    { to: '/admin/orders', label: t.admin.orders, icon: 'orders', show: true },
    { to: '/admin/products', label: t.admin.products, icon: 'products', show: isAdmin },
    { to: '/admin/categories', label: t.admin.categories, icon: 'categories', show: isAdmin },
    { to: '/admin/customers', label: t.admin.customers, icon: 'customers', show: isAdmin },
    { to: '/admin/import', label: t.admin.import, icon: 'import', show: isAdmin },
  ];

  return (
    <div className="admin-shell">
      <aside className={`admin-sidebar ${open ? 'open' : ''}`}>
        <Link to="/admin" className="brand admin-brand">
          <Logo size={30} />
          <span className="brand-name">{t.brand}</span>
          <span className="pill">{t.admin.title}</span>
        </Link>
        <nav className="admin-nav" onClick={() => setOpen(false)}>
          {links.filter((l) => l.show).map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end}>
              <Icon name={l.icon} />
              <span>{l.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="admin-sidebar-foot">
          <div className="small muted">{t.admin.signedInAs}</div>
          <div className="admin-user">
            {user?.fullName || user?.email}
            <span className="pill pill-muted">{user?.role}</span>
          </div>
          {!isAdmin && <div className="small muted">{t.admin.readOnlyRole}</div>}
          <div className="admin-foot-actions">
            <button className="lang-switch" onClick={() => switchLang()}>{t.switchTo}</button>
            <Link to="/" className="link-btn">{t.admin.backToStore}</Link>
            <button className="link-btn" onClick={() => { logout(); navigate('/'); }}>{t.nav.logout}</button>
          </div>
        </div>
      </aside>
      {open && <div className="admin-scrim" onClick={() => setOpen(false)} />}
      <div className="admin-main">
        <div className="admin-topbar">
          <button className="icon-btn" onClick={() => setOpen(true)} aria-label={t.nav.menu}>
            <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
          <span className="brand-name">{t.brand} · {t.admin.title}</span>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
