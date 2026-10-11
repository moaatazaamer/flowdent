import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import AdminPurchases, { PurchaseForm } from './AdminPurchases';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi, stockRow, today } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);
const providers = [
  { id: 1, name: 'Nile Dental Supplies', active: true },
  { id: 2, name: 'Old Supplier', active: false },
];

function purchase(overrides = {}) {
  return {
    id: 7, supplier: { id: 1, name: 'Nile Dental Supplies' }, status: 'ORDERED', purchaseDate: '2026-10-01',
    invoiceNumber: 'INV-55', shippingCost: 60, customsCost: 40, otherCost: 0, notes: null,
    goodsTotal: 1000, extraCosts: 100, totalCost: 1100,
    items: [{ id: 1, product: { id: 10, name: 'Nitrile Gloves M', sku: 'GLO-001' }, quantity: 10, unitCost: 100, landedUnitCost: null }],
    ...overrides,
  };
}

describe('PurchaseForm', () => {
  test('totals goods + extra costs and sends the purchase', async () => {
    const api = mockApi({
      get: { '/admin/stock': [stockRow({ costPrice: 100 })], '/admin/suppliers': providers },
      post: { '/admin/purchases': purchase({ status: 'RECEIVED' }) },
    });
    const onSaved = jest.fn();
    wrap(<PurchaseForm open onClose={() => {}} onSaved={onSaved} />);

    await screen.findByText('Nile Dental Supplies');
    expect(screen.queryByText('Old Supplier')).not.toBeInTheDocument();   // inactive providers hidden

    fireEvent.change(screen.getByLabelText(en.ops.provider), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText(en.ops.invoice), { target: { value: 'INV-55' } });
    fireEvent.change(screen.getByDisplayValue(/Choose product/), { target: { value: '10' } });
    expect(screen.getByLabelText(/Unit cost/)).toHaveValue(100);           // defaults to current cost price
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText(en.ops.shipping), { target: { value: '60' } });
    fireEvent.change(screen.getByLabelText(en.ops.customs), { target: { value: '40' } });
    fireEvent.change(screen.getByLabelText(new RegExp(en.ops.paidToProvider)), { target: { value: '600' } });

    const totals = screen.getByText(en.ops.totalCost).closest('.summary-row');
    expect(totals).toHaveTextContent(/EGP\s?1,100\.00/);
    expect(screen.getByLabelText(en.ops.receivedNow)).toBeChecked();

    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith('/admin/purchases', {
      supplierId: 1,
      purchaseDate: today(),
      invoiceNumber: 'INV-55',
      shippingCost: 60,
      customsCost: 40,
      otherCost: 0,
      notes: '',
      receivedNow: true,
      amountPaid: 600,
      paymentMethod: 'Cash',
      items: [{ productId: 10, quantity: 10, unitCost: 100 }],
    });
  });

  test('editing an ordered purchase loads its lines and uses PUT', async () => {
    const api = mockApi({
      get: { '/admin/stock': [stockRow()], '/admin/suppliers': providers },
      put: { '/admin/purchases/7': purchase() },
    });
    wrap(<PurchaseForm open purchase={purchase()} onClose={() => {}} />);
    await screen.findByText('Nile Dental Supplies');

    expect(screen.getByLabelText(en.ops.invoice)).toHaveValue('INV-55');
    expect(screen.getByLabelText(en.ops.qty)).toHaveValue(10);
    expect(screen.getByLabelText(en.ops.receivedNow)).not.toBeChecked();
    expect(screen.queryByLabelText(new RegExp(en.ops.paidToProvider))).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '12' } });
    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.put).toHaveBeenCalled());
    const [url, body] = api.put.mock.calls[0];
    expect(url).toBe('/admin/purchases/7');
    expect(body.items).toEqual([{ productId: 10, quantity: 12, unitCost: 100 }]);
    expect(body.amountPaid).toBeNull();
  });
});

describe('Purchases page', () => {
  test('lists purchases and receives an ordered one', async () => {
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    const api = mockApi({
      get: { '/admin/purchases': [purchase(), purchase({ id: 8, status: 'RECEIVED', invoiceNumber: 'INV-56' })] },
      post: { '/admin/purchases/7/receive': purchase({ status: 'RECEIVED' }) },
    });
    wrap(<AdminPurchases />);

    const row = (await screen.findByText('INV-55')).closest('tr');
    expect(within(row).getByText(en.ops.purchaseStatus.ORDERED)).toBeInTheDocument();
    const receivedRow = screen.getByText('INV-56').closest('tr');
    expect(within(receivedRow).queryByText(en.ops.receive)).not.toBeInTheDocument();

    fireEvent.click(within(row).getByText(en.ops.receive));
    await waitFor(() => expect(within(row).getByText(en.ops.purchaseStatus.RECEIVED)).toBeInTheDocument());
    expect(api.post).toHaveBeenCalledWith('/admin/purchases/7/receive', {});
  });

  test('status filter narrows the list', async () => {
    mockApi({ get: { '/admin/purchases': [purchase(), purchase({ id: 8, status: 'CANCELLED', invoiceNumber: 'INV-99' })] } });
    wrap(<AdminPurchases />);
    await screen.findByText('INV-99');
    fireEvent.change(screen.getByDisplayValue(en.ops.all), { target: { value: 'CANCELLED' } });
    expect(screen.queryByText('INV-55')).not.toBeInTheDocument();
    expect(screen.getByText('INV-99')).toBeInTheDocument();
  });
});
