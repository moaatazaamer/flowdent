import { useState } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Balance, LineItems, linesTotal, PaymentForm, SaleForm } from './shared';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi, stockRow, today } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);
const clients = [
  { id: 5, name: 'Smile Dental Clinic', city: 'Cairo', address: '12 Nasr St', balance: 500 },
  { id: 6, name: 'Dr. Omar Khaled', city: 'Giza', balance: 0 },
];

describe('linesTotal', () => {
  test('adds quantity × price and ignores blanks', () => {
    expect(linesTotal([{ quantity: '2', price: '120' }, { quantity: 3, price: 10.5 }, { quantity: '', price: '9' }])).toBe(271.5);
    expect(linesTotal([])).toBe(0);
  });
});

describe('Balance', () => {
  test('colours what is owed, credit and zero differently', () => {
    const { container } = wrap(<><Balance value={500} lang="en" /><Balance value={-200} lang="en" /><Balance value={0} lang="en" /></>);
    const spans = container.querySelectorAll('.bal');
    expect(spans[0]).toHaveClass('bal-owed');
    expect(spans[1]).toHaveClass('bal-credit');
    expect(spans[2]).toHaveClass('bal-zero');
    expect(spans[0]).toHaveTextContent(/EGP\s?500\.00/);
  });
});

describe('LineItems', () => {
  function Harness() {
    const [lines, setLines] = useState([{ productId: '', quantity: 1, price: '' }]);
    return (
      <>
        <LineItems lines={lines} setLines={setLines} products={[stockRow()]} priceLabel="Unit price" priceField="costPrice" />
        <output data-testid="total">{linesTotal(lines)}</output>
      </>
    );
  }

  test('picking a product fills its price; lines can be added and removed', () => {
    wrap(<Harness />);
    fireEvent.change(screen.getByDisplayValue(en.ops.chooseProduct), { target: { value: '10' } });
    expect(screen.getByLabelText('Unit price')).toHaveValue(95);
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '4' } });
    expect(screen.getByTestId('total')).toHaveTextContent('380');

    fireEvent.click(screen.getByText(en.ops.addLine));
    expect(screen.getAllByLabelText(en.ops.qty)).toHaveLength(2);
    fireEvent.click(screen.getAllByText(en.ops.removeLine)[1]);
    expect(screen.getAllByLabelText(en.ops.qty)).toHaveLength(1);
    expect(screen.getByText(en.ops.removeLine)).toBeDisabled();
  });
});

describe('SaleForm', () => {
  test('records a phone sale with a special price and a deposit', async () => {
    const api = mockApi({
      get: { '/admin/stock': [stockRow()], '/admin/clients': clients },
      post: { '/admin/clients/sales': { id: 77, totalAmount: 240 } },
    });
    const onSaved = jest.fn();
    const onClose = jest.fn();
    wrap(<SaleForm open onClose={onClose} onSaved={onSaved} />);

    await screen.findByText('Smile Dental Clinic · Cairo');
    fireEvent.change(screen.getByLabelText(en.ops.client), { target: { value: '5' } });
    fireEvent.change(screen.getByDisplayValue(/Choose product/), { target: { value: '10' } });
    expect(screen.getByLabelText(en.ops.unitPrice)).toHaveValue(150);
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText(en.ops.unitPrice), { target: { value: '120' } });
    fireEvent.change(screen.getByLabelText(new RegExp(en.ops.paidNow)), { target: { value: '100' } });
    expect(screen.getAllByText(/EGP\s?240\.00/).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByText(en.common.save));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith({ id: 77, totalAmount: 240 }));
    expect(onClose).toHaveBeenCalled();
    expect(api.post).toHaveBeenCalledWith('/admin/clients/sales', {
      clientId: 5,
      items: [{ productId: 10, quantity: 2, unitPrice: 120 }],
      paymentMethod: 'CREDIT',
      deliveryMethod: 'HOME_DELIVERY',
      deliveryAddress: '',
      notes: '',
      amountPaidNow: 100,
      paymentNote: '',
    });
  });

  test('the client is fixed when opened from a client account', async () => {
    mockApi({ get: { '/admin/stock': [stockRow()], '/admin/clients': clients } });
    wrap(<SaleForm open clientId={6} onClose={() => {}} />);
    await screen.findByText('Dr. Omar Khaled · Giza');
    const select = screen.getByLabelText(en.ops.client);
    expect(select).toHaveValue('6');
    expect(select).toBeDisabled();
  });

  test('server errors are shown in the form', async () => {
    mockApi({
      get: { '/admin/stock': [stockRow()], '/admin/clients': clients },
      post: { '/admin/clients/sales': Object.assign(new Error('x'), { response: { data: { message: 'Not enough stock for "Gloves"' } } }) },
    });
    const onClose = jest.fn();
    wrap(<SaleForm open clientId={5} onClose={onClose} />);
    await screen.findByText('Smile Dental Clinic · Cairo');
    fireEvent.change(screen.getByDisplayValue(/Choose product/), { target: { value: '10' } });
    fireEvent.click(screen.getByText(en.common.save));
    expect(await screen.findByText('Not enough stock for "Gloves"')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe('PaymentForm', () => {
  test('pre-fills the open balance and links the payment to an order', async () => {
    const api = mockApi({ post: { '/admin/clients/payments': { id: 1 } } });
    const onSaved = jest.fn();
    wrap(
      <PaymentForm open kind="client" partyId={5} partyName="Smile" defaultAmount={500}
        links={[{ id: 77, label: '#77 · 240' }]} onClose={() => {}} onSaved={onSaved} />
    );

    expect(screen.getByLabelText(new RegExp(en.ops.amount))).toHaveValue(500);
    fireEvent.change(screen.getByLabelText(en.ops.forOrder), { target: { value: '77' } });
    fireEvent.change(screen.getByLabelText(en.ops.method), { target: { value: 'InstaPay' } });
    fireEvent.click(screen.getByText(en.common.save));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith('/admin/clients/payments', {
      amount: 500, paidAt: today(), method: 'InstaPay', notes: '', clientId: 5, orderId: 77,
    });
  });

  test('provider payments go to the provider endpoint', async () => {
    const api = mockApi({ post: { '/admin/suppliers/payments': { id: 2 } } });
    wrap(<PaymentForm open kind="provider" partyId={1} partyName="Nile" defaultAmount={0} onClose={() => {}} />);
    fireEvent.change(screen.getByLabelText(new RegExp(en.ops.amount)), { target: { value: '250' } });
    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/suppliers/payments', {
      amount: 250, paidAt: today(), method: 'Cash', notes: '', supplierId: 1, purchaseId: null,
    }));
  });
});
