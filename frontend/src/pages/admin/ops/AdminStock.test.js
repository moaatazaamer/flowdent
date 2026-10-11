import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import AdminStock from './AdminStock';
import { LangProvider } from '../../../context/LangContext';
import en from '../../../i18n/en';
import { mockApi, stockRow } from '../../../test-utils';

const wrap = (ui) => render(<LangProvider>{ui}</LangProvider>);

const rows = [
  stockRow(),
  stockRow({ id: 11, name: 'Gracey Curette', sku: 'HAN-003', stockQuantity: 3, reorderLevel: 10,
    stockStatus: 'LOW', costPrice: 585, price: 975, stockValue: 1755, onOrder: 5 }),
  stockRow({ id: 12, name: 'Alginate 453g', sku: 'IMP-001', stockQuantity: 0, stockStatus: 'OUT', stockValue: 0 }),
];

describe('Stock page', () => {
  test('shows totals and low / out counts', async () => {
    mockApi({ get: { '/admin/stock': rows } });
    wrap(<AdminStock />);
    await screen.findByText('Gracey Curette');

    const valueTile = screen.getByText(en.ops.stockValue, { selector: '.stat-label' }).closest('.stat');
    expect(valueTile).toHaveTextContent(/EGP\s?6,505\.00/);           // 4750 + 1755 + 0
    expect(screen.getByText(en.ops.lowCount).closest('.stat')).toHaveTextContent('1');
    expect(screen.getByText(en.ops.outCount).closest('.stat')).toHaveTextContent('1');
  });

  test('low/out filter and search narrow the list', async () => {
    mockApi({ get: { '/admin/stock': rows } });
    wrap(<AdminStock />);
    await screen.findByText('Gracey Curette');

    fireEvent.click(screen.getByLabelText(en.ops.lowOnly));
    expect(screen.queryByText('Nitrile Gloves M')).not.toBeInTheDocument();
    expect(screen.getByText('Gracey Curette')).toBeInTheDocument();
    expect(screen.getByText('Alginate 453g')).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText(en.ops.search), { target: { value: 'imp-001' } });
    expect(screen.queryByText('Gracey Curette')).not.toBeInTheDocument();
    expect(screen.getByText('Alginate 453g')).toBeInTheDocument();
  });

  test('removing stock sends a negative adjustment and reloads', async () => {
    const api = mockApi({ get: { '/admin/stock': rows }, post: { '/admin/stock/adjust': rows[1] } });
    wrap(<AdminStock />);
    const row = (await screen.findByText('Gracey Curette')).closest('tr');

    fireEvent.click(within(row).getByText(en.ops.adjust));
    fireEvent.click(screen.getByText(en.ops.removeStock));
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText(en.ops.notes), { target: { value: 'used for demo' } });
    fireEvent.click(screen.getByText(en.common.save));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/stock/adjust', {
      productId: 11, quantityChange: -2, type: 'ADJUSTMENT', note: 'used for demo',
    }));
    await waitFor(() => expect(api.get.mock.calls.filter(([u]) => u === '/admin/stock').length).toBe(2));
  });

  test('write-off is sent as DAMAGE; stock count sends the counted quantity', async () => {
    const api = mockApi({ get: { '/admin/stock': rows } });
    wrap(<AdminStock />);
    const row = (await screen.findByText('Nitrile Gloves M')).closest('tr');

    fireEvent.click(within(row).getByText(en.ops.adjust));
    fireEvent.click(screen.getByText(en.ops.damage));
    fireEvent.change(screen.getByLabelText(en.ops.qty), { target: { value: '3' } });
    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/stock/adjust',
      expect.objectContaining({ productId: 10, quantityChange: -3, type: 'DAMAGE' })));

    fireEvent.click(within(row).getByText(en.ops.count));
    expect(screen.getByLabelText(en.ops.countedQty)).toHaveValue(50);
    fireEvent.change(screen.getByLabelText(en.ops.countedQty), { target: { value: '47' } });
    fireEvent.click(screen.getByText(en.common.save));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/stock/count',
      { productId: 10, countedQuantity: 47, note: '' }));
  });

  test('history shows each movement with sign and type', async () => {
    mockApi({
      get: {
        '/admin/stock': rows,
        '/admin/stock/movements': [
          { id: 2, createdAt: '2026-10-05T10:00:00', type: 'SALE', quantityChange: -3, stockAfter: 47, reference: 'Order #12', createdBy: 'admin', product: rows[0] },
          { id: 1, createdAt: '2026-10-01T09:00:00', type: 'PURCHASE', quantityChange: 50, stockAfter: 50, reference: 'Purchase #4', product: rows[0] },
        ],
      },
    });
    wrap(<AdminStock />);
    const row = (await screen.findByText('Nitrile Gloves M')).closest('tr');
    fireEvent.click(within(row).getByText(en.ops.history));

    expect(await screen.findByText('Order #12')).toBeInTheDocument();
    expect(screen.getByText('-3')).toHaveClass('neg');
    expect(screen.getByText('+50')).toHaveClass('pos');
    expect(screen.getByText(en.ops.movementType.PURCHASE)).toBeInTheDocument();
  });
});
