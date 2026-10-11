import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminFinance from './AdminFinance';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);

const summary = {
  from: '2026-10-01', to: '2026-10-11', sales: 600, orderCount: 1, returns: 200, netSales: 400,
  costOfGoods: 220, grossProfit: 180, grossMargin: 45.0, expenses: 300, writeOffs: 0, netProfit: -120,
  purchases: 1000, extraCosts: 100, extraCostsByType: { SHIPPING: 60, CUSTOMS: 40, OTHER: 0 },
  expensesByCategory: { RENT: 300, SALARIES: 0, UTILITIES: 0, TRANSPORT: 0, MARKETING: 0, MAINTENANCE: 0, BANK_FEES: 0, TAXES: 0, OTHER: 0 },
  moneyIn: 600, moneyOut: 1000,
  profitByCategory: [{ category: 'Restorative Materials', sales: 600, cost: 330, profit: 270 }],
  stockValue: 660, clientsOwe: 450, weOweProviders: 180,
};

const monthly = [
  { month: '2026-09', netSales: 0, costOfGoods: 0, expenses: 0, extraCosts: 0, netProfit: 0 },
  { month: '2026-10', netSales: 400, costOfGoods: 220, expenses: 300, extraCosts: 100, netProfit: -120 },
];

const pad = (n) => String(n).padStart(2, '0');
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

describe('Finance page', () => {
  test('shows profit, position and breakdowns for this month', async () => {
    const api = mockApi({ get: { '/admin/finance/summary': summary, '/admin/finance/monthly': monthly } });
    wrap(<AdminFinance />);

    const netTile = (await screen.findByText(en.ops.netProfit, { selector: '.stat-label' })).closest('.stat');
    expect(netTile).toHaveTextContent(/-EGP\s?120\.00|EGP\s?-120\.00/);
    expect(netTile).toHaveClass('stat-bad');
    expect(screen.getByText('45% margin')).toBeInTheDocument();

    const position = screen.getByText(en.ops.clientsOwe).closest('.summary-row');
    expect(position).toHaveTextContent(/EGP\s?450\.00/);
    expect(screen.getByText(en.ops.youOwe).closest('.summary-row')).toHaveTextContent(/EGP\s?180\.00/);
    expect(screen.getByText('Restorative Materials')).toBeInTheDocument();
    expect(screen.getByText(en.ops.expenseCategory.RENT)).toBeInTheDocument();
    expect(screen.queryByText(en.ops.expenseCategory.SALARIES)).not.toBeInTheDocument();  // zero rows hidden
    expect(screen.getByText('2026-10')).toBeInTheDocument();

    const now = new Date();
    expect(api.get).toHaveBeenCalledWith('/admin/finance/summary', {
      params: { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now) },
    });
  });

  test('"Last month" asks for the whole previous month', async () => {
    const api = mockApi({ get: { '/admin/finance/summary': summary, '/admin/finance/monthly': monthly } });
    wrap(<AdminFinance />);
    await screen.findByText(en.ops.netProfit, { selector: '.stat-label' });

    fireEvent.click(screen.getByText(en.ops.lastMonth));
    const now = new Date();
    const from = iso(new Date(now.getFullYear(), now.getMonth() - 1, 1));
    const to = iso(new Date(now.getFullYear(), now.getMonth(), 0));
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/admin/finance/summary', { params: { from, to } }));
  });

  test('custom range shows date inputs and refetches when changed', async () => {
    const api = mockApi({ get: { '/admin/finance/summary': summary, '/admin/finance/monthly': [] } });
    wrap(<AdminFinance />);
    await screen.findByText(en.ops.netProfit, { selector: '.stat-label' });

    fireEvent.click(screen.getByText(en.ops.custom));
    fireEvent.change(screen.getByLabelText(en.ops.from), { target: { value: '2026-01-01' } });
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/admin/finance/summary',
      { params: expect.objectContaining({ from: '2026-01-01' }) }));
  });
});
