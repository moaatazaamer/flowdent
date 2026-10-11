import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import AdminClients from './AdminClients';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi, today } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);

const smile = {
  id: 5, name: 'Smile Dental Clinic', type: 'CLINIC', contactName: 'Dr. Sara', phone: '+20 100 111 2233',
  city: 'Cairo', address: '12 Nasr St', orderCount: 2, totalSales: 1500, totalPaid: 1000, totalReturned: 0,
  balance: 500, lastOrder: '2026-10-05T10:00:00', registered: false,
};
const omar = { ...smile, id: 6, name: 'Dr. Omar Khaled', type: 'DOCTOR', contactName: null, city: 'Giza',
  phone: '+20 122 333 4455', totalSales: 300, totalPaid: 300, balance: 0, orderCount: 1 };

const account = {
  summary: smile,
  orders: [{ id: 77, createdAt: '2026-10-05T10:00:00', totalAmount: 900, paid: false, status: 'CONFIRMED',
    paymentMethod: 'CREDIT', items: [{ id: 1, quantity: 6, product: { name: 'Gloves' } }] }],
  payments: [{ id: 3, paidAt: '2026-10-06', amount: 400, method: 'Cash', orderId: null, notes: 'deposit' }],
  returns: [],
};

describe('Clients page', () => {
  test('lists clients with balances; filters by open balance and type', async () => {
    mockApi({ get: { '/admin/clients': [smile, omar] } });
    wrap(<AdminClients />);

    const row = (await screen.findByText('Smile Dental Clinic')).closest('tr');
    expect(within(row).getByText(/EGP\s?500\.00/)).toHaveClass('bal-owed');
    const owedTile = screen.getByText(en.ops.clientOwes, { selector: '.stat-label' }).closest('.stat');
    expect(owedTile).toHaveTextContent(/EGP\s?500\.00/);

    fireEvent.click(screen.getByLabelText(en.ops.onlyWithBalance));
    expect(screen.queryByText('Dr. Omar Khaled')).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText(en.ops.onlyWithBalance));

    fireEvent.change(screen.getByDisplayValue(en.ops.all), { target: { value: 'DOCTOR' } });
    expect(screen.queryByText('Smile Dental Clinic')).not.toBeInTheDocument();
    expect(screen.getByText('Dr. Omar Khaled')).toBeInTheDocument();
  });

  test('adding a client opens their account', async () => {
    const api = mockApi({
      get: { '/admin/clients': [], '/admin/clients/9': { ...account, summary: { ...smile, id: 9, name: 'New Lab', balance: 0 }, orders: [], payments: [] } },
      post: { '/admin/clients': { ...smile, id: 9, name: 'New Lab' } },
    });
    wrap(<AdminClients />);
    expect(await screen.findByText(en.ops.noClients)).toBeInTheDocument();

    fireEvent.click(screen.getByText(`+ ${en.ops.newClient}`));
    fireEvent.change(screen.getByLabelText(en.ops.clientName), { target: { value: 'New Lab' } });
    fireEvent.change(screen.getByLabelText(en.ops.type), { target: { value: 'LAB' } });
    fireEvent.change(screen.getByLabelText(en.ops.phone), { target: { value: '+20 155 000' } });
    fireEvent.click(screen.getByText(en.common.save));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/clients', expect.objectContaining({
      name: 'New Lab', type: 'LAB', phone: '+20 155 000',
    })));
    expect(await screen.findByText(/New Lab ·/)).toBeInTheDocument();     // account modal title
  });

  test('client account shows orders and payments, and records a payment for the open balance', async () => {
    const api = mockApi({
      get: { '/admin/clients': [smile, omar], '/admin/clients/5': account },
      post: { '/admin/clients/payments': { id: 4 } },
    });
    wrap(<AdminClients />);
    fireEvent.click(await screen.findByText('Smile Dental Clinic'));

    const dialog = (await screen.findByText(/Smile Dental Clinic · Clinic/)).closest('.modal');
    expect(within(dialog).getByText('Gloves × 6')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByText(new RegExp(`^${en.ops.payments}`)));
    expect(within(dialog).getByText('deposit')).toBeInTheDocument();

    fireEvent.click(within(dialog).getByText(en.ops.recordPayment));
    const amount = await screen.findByLabelText(new RegExp(en.ops.amount));
    expect(amount).toHaveValue(500);
    fireEvent.change(screen.getByLabelText(en.ops.forOrder), { target: { value: '77' } });
    fireEvent.click(screen.getAllByText(en.common.save).pop());

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/clients/payments', {
      amount: 500, paidAt: today(), method: 'Cash', notes: '', clientId: 5, orderId: 77,
    }));
    // Account and list are refreshed after the payment.
    await waitFor(() => expect(api.get.mock.calls.filter(([u]) => u === '/admin/clients/5').length).toBe(2));
  });
});
