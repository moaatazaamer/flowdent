import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminReturns from './AdminReturns';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi, stockRow, today } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);

const existing = [
  { id: 1, type: 'CUSTOMER_RETURN', returnDate: '2026-10-02', product: { id: 10, name: 'Nitrile Gloves M' },
    quantity: 1, amount: 150, settlement: 'CREDIT', client: { id: 5, name: 'Smile Dental Clinic' },
    orderId: 77, backToStock: false, reason: 'torn box' },
  { id: 2, type: 'SUPPLIER_RETURN', returnDate: '2026-10-03', product: { id: 10, name: 'Nitrile Gloves M' },
    quantity: 4, amount: 380, settlement: 'CASH', supplier: { id: 1, name: 'Nile Dental Supplies' }, reason: 'expired' },
];

function setup(extra = {}) {
  return mockApi({
    get: {
      '/admin/returns': existing,
      '/admin/stock': [stockRow()],
      '/admin/clients': [{ id: 5, name: 'Smile Dental Clinic' }],
      '/admin/suppliers': [{ id: 1, name: 'Nile Dental Supplies' }],
    },
    post: { '/admin/returns': { id: 3 } },
    ...extra,
  });
}

describe('Returns page', () => {
  test('lists returns in both directions with totals', async () => {
    setup();
    wrap(<AdminReturns />);
    expect(await screen.findByText('torn box')).toBeInTheDocument();
    expect(screen.getByText('expired')).toBeInTheDocument();
    expect(screen.getByText(/✕/)).toBeInTheDocument();            // not put back in stock
    expect(screen.getAllByText(/EGP\s?380\.00/).length).toBeGreaterThan(0);

    fireEvent.change(screen.getByDisplayValue(en.ops.all), { target: { value: 'SUPPLIER_RETURN' } });
    expect(screen.queryByText('torn box')).not.toBeInTheDocument();
  });

  test('client return: value suggested from the selling price, restocked as credit', async () => {
    const api = setup();
    wrap(<AdminReturns />);
    fireEvent.click(await screen.findByText(`+ ${en.ops.newReturn}`));

    await screen.findByText('Smile Dental Clinic', { selector: 'option' });
    fireEvent.change(screen.getByLabelText(en.ops.client), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText(en.ops.orderNo), { target: { value: '#77' } });
    fireEvent.change(screen.getByLabelText(en.ops.product), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '2' } });
    await waitFor(() => expect(screen.getByLabelText(/Value/)).toHaveValue(300));    // 2 × 150
    expect(screen.getByLabelText(en.ops.backToStock)).toBeChecked();
    fireEvent.change(screen.getByLabelText(en.ops.reason), { target: { value: 'wrong size' } });

    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith('/admin/returns', {
      type: 'CUSTOMER_RETURN', returnDate: today(), productId: 10, quantity: 2, amount: 300,
      settlement: 'CREDIT', clientId: 5, orderId: 77, supplierId: null, backToStock: true, reason: 'wrong size',
    });
  });

  test('return to provider: value suggested from cost price, never restocked', async () => {
    const api = setup();
    wrap(<AdminReturns />);
    fireEvent.click(await screen.findByText(`+ ${en.ops.newReturn}`));
    fireEvent.click(screen.getByText(en.ops.toProvider));

    expect(screen.queryByLabelText(en.ops.backToStock)).not.toBeInTheDocument();
    await screen.findByText('Nile Dental Supplies', { selector: 'option' });
    fireEvent.change(screen.getByLabelText(en.ops.provider), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText(en.ops.product), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '2' } });
    await waitFor(() => expect(screen.getByLabelText(/Value/)).toHaveValue(190));    // 2 × cost 95
    fireEvent.change(screen.getByLabelText(en.ops.settlement), { target: { value: 'CASH' } });

    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    const body = api.post.mock.calls[0][1];
    expect(body).toEqual(expect.objectContaining({
      type: 'SUPPLIER_RETURN', supplierId: 1, clientId: null, orderId: null, backToStock: false,
      amount: 190, settlement: 'CASH', quantity: 2,
    }));
  });

  test('a typed value is not overwritten by the suggestion', async () => {
    const api = setup();
    wrap(<AdminReturns />);
    fireEvent.click(await screen.findByText(`+ ${en.ops.newReturn}`));
    await screen.findByText('Smile Dental Clinic', { selector: 'option' });
    fireEvent.change(screen.getByLabelText(en.ops.client), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText(en.ops.product), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText(/Value/), { target: { value: '99' } });
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '3' } });
    expect(screen.getByLabelText(/Value/)).toHaveValue(99);

    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    expect(api.post.mock.calls[0][1].amount).toBe(99);
  });
});
