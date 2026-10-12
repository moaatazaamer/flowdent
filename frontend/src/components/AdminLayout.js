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
  clients: 'M4 21V8l8-5 8 5v13M9 21v-6h6v6M12 8v4M10 10h4',
  providers: 'M3 7h11v9H3zM14 10h4l3 3v3h-7M7 19a2 2 0 100-4 2 2 0 000 4zm10 0a2 2 0 100-4 2 2 0 000 4z',
  purchases: 'M4 4h2l2 11h10l2-8H7M10 20a1 1 0 100-2 1 1 0 000 2zm7 0a1 1 0 100-2 1 1 0 000 2z',
  returns: 'M9 14L4 9l5-5M4 9h11a5 5 0 010 10h-3',
  stock: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4',
  expenses: 'M4 6h16v12H4zM4 10h16M8 15h3',
  finance: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
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

  const groups = [
    { label: null, links: [
      { to: '/admin', label: t.admin.dashboard, icon: 'dashboard', end: true, show: true },
    ] },
    { label: t.ops.groupSales, links: [
      { to: '/admin/orders', label: t.admin.orders, icon: 'orders', show: true },
      { to: '/admin/clients', label: t.ops.clients, icon: 'clients', show: isAdmin },
    ] },
    { label: t.ops.groupOperations, links: [
      { to: '/admin/purchases', label: t.ops.purchases, icon: 'purchases', show: isAdmin },
      { to: '/admin/providers', label: t.ops.providers, icon: 'providers', show: isAdmin },
      { to: '/admin/returns', label: t.ops.returns, icon: 'returns', show: isAdmin },
      { to: '/admin/stock', label: t.ops.stock, icon: 'stock', show: isAdmin },
    ] },
    { label: t.ops.groupMoney, links: [
      { to: '/admin/expenses', label: t.ops.expenses, icon: 'expenses', show: isAdmin },
      { to: '/admin/finance', label: t.ops.finance, icon: 'finance', show: isAdmin },
    ] },
    { label: t.ops.groupCatalog, links: [
      { to: '/admin/products', label: t.admin.products, icon: 'products', show: isAdmin },
      { to: '/admin/categories', label: t.admin.categories, icon: 'categories', show: isAdmin },
      { to: '/admin/import', label: t.admin.import, icon: 'import', show: isAdmin },
    ] },
  ];

  return (
    <div className="admin-shell">
      <aside className={`admin-sidebar ${open ? 'open' : ''}`}>
        <Link to="/admin" className="brand admin-brand">
          <Logo size={38} variant="white" />
          <span className="pill">{t.admin.title}</span>
        </Link>
        <nav className="admin-nav" onClick={() => setOpen(false)}>
          {groups.map((g, gi) => {
            const visible = g.links.filter((l) => l.show);
            if (!visible.length) return null;
            return (
              <div key={gi} className="admin-nav-group">
                {g.label && <div className="admin-nav-label">{g.label}</div>}
                {visible.map((l) => (
                  <NavLink key={l.to} to={l.to} end={l.end}>
                    <Icon name={l.icon} />
                    <span>{l.label}</span>
                  </NavLink>
                ))}
              </div>
            );
          })}
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
          <Logo size={30} />
          <span className="admin-topbar-title">{t.admin.title}</span>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
