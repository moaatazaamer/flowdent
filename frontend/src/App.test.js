import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';
import en from './i18n/en';
import { mockApi } from './test-utils';

const dashboard = {
  totalRevenue: 1500, paidRevenue: 1000, totalOrders: 3, pendingOrders: 1, totalCustomers: 2, totalProducts: 31,
  ordersByStatus: { PENDING: 1, CONFIRMED: 1, SHIPPED: 0, DELIVERED: 1, CANCELLED: 0, READY_FOR_PICKUP: 0, PICKED_UP: 0 },
  ordersByPaymentMethod: { ONLINE_PAYMENT: 0, CASH_ON_DELIVERY: 1, POS_ON_DELIVERY: 0, BANK_TRANSFER: 0, CREDIT: 2 },
  dailyStats: [{ date: '2026-10-10', revenue: 1500, orders: 3 }],
  topProducts: [], lowStockProducts: [], recentOrders: [],
};

function signIn(role) {
  localStorage.setItem('flowdent_token', 'jwt');
  localStorage.setItem('flowdent_user', JSON.stringify({ id: 1, email: role.toLowerCase(), role, fullName: `${role} user` }));
  return mockApi({
    get: {
      '/auth/me': { id: 1, email: role.toLowerCase(), role, fullName: `${role} user` },
      '/payment/config': { currency: 'egp', enabled: false },
      '/admin/dashboard': dashboard,
    },
  });
}

describe('App routing and roles', () => {
  test('visitors opening /admin are sent to the sign-in page', async () => {
    mockApi({ get: { '/payment/config': { currency: 'egp' } } });
    window.history.pushState({}, '', '/admin');
    render(<App />);
    expect(await screen.findByText(en.auth.loginTitle)).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  test('admins see every section of the back office', async () => {
    signIn('ADMIN');
    window.history.pushState({}, '', '/admin');
    render(<App />);

    const nav = (await screen.findByText(en.simple.sell, { selector: 'nav a span' })).closest('nav');
    // The short menu: the everyday things only.
    for (const label of [en.admin.dashboard, en.simple.sell, en.ops.clients, en.simple.buy, en.ops.stock,
      en.simple.money, en.admin.products]) {
      expect(within(nav).getByText(label)).toBeInTheDocument();
    }
    expect(within(nav).queryByText(en.ops.providers)).not.toBeInTheDocument();
    // Everything else is behind "More".
    fireEvent.click(within(nav).getByText(en.simple.more, { exact: false }));
    for (const label of [en.old.menuTitle, en.ops.expenses, en.ops.providers, en.ops.returns,
      en.admin.categories, en.admin.import]) {
      expect(within(nav).getByText(label)).toBeInTheDocument();
    }
    expect(await screen.findByText(en.simple.welcome)).toBeInTheDocument();
    expect(await screen.findByText(en.admin.revenue)).toBeInTheDocument();
  });

  test('order staff only see the dashboard and orders, and are kept out of other pages', async () => {
    signIn('ORDER_ADMIN');
    window.history.pushState({}, '', '/admin/finance');
    render(<App />);

    await waitFor(() => expect(window.location.pathname).toBe('/admin/orders'));
    const nav = (await screen.findByText(en.simple.sell, { selector: 'nav a span' })).closest('nav');
    expect(within(nav).queryByText(en.ops.clients)).not.toBeInTheDocument();
    expect(within(nav).queryByText(en.ops.finance)).not.toBeInTheDocument();
    expect(screen.getByText(en.admin.readOnlyRole)).toBeInTheDocument();
  });

  test('signed-in customers trying the admin end up back in the shop', async () => {
    signIn('CUSTOMER');
    window.history.pushState({}, '', '/admin');
    render(<App />);
    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(screen.queryByText(en.simple.welcome)).not.toBeInTheDocument();
    expect(await screen.findByText(en.home.heroTitle)).toBeInTheDocument();
  });
});
