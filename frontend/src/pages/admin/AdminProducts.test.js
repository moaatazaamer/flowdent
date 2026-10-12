import { fireEvent, screen } from '@testing-library/react';
import AdminProducts from './AdminProducts';
import en from '../../i18n/en';
import { mockApi, renderWithProviders, stockRow } from '../../test-utils';

const cat = { id: 1, name: 'Patient Protection', nameAr: 'حماية المريض' };
const products = [
  { id: 1, name: 'Dental Bib', nameAr: 'مريلة أسنان', sku: 'ORA-001', brand: 'Ora', price: 234, stockQuantity: 0, category: cat },
  { id: 2, name: 'Bib Holder', nameAr: 'حامل المريلة', sku: 'ORA-002', brand: 'Ora', price: 40, stockQuantity: 30, category: cat },
  { id: 3, name: 'Cotton Rolls', nameAr: 'لفائف قطنية', sku: 'ORA-006', brand: 'Ora', price: 46.8, stockQuantity: 5, category: cat },
];
const stock = [
  stockRow({ id: 1, costPrice: 234, marginPercent: 0 }),
  stockRow({ id: 2, costPrice: 18.2, marginPercent: 54.5 }),
  stockRow({ id: 3, costPrice: 46.8, marginPercent: 0 }),
];
const rows = () => [...document.querySelectorAll('tbody tr strong')].map((s) => s.textContent);

describe('Admin products: selling price not set yet', () => {
  test('warns about products priced at cost and can list only those', async () => {
    mockApi({ get: { '/products': products, '/categories': [cat], '/admin/stock': stock } });
    renderWithProviders(<AdminProducts />);

    expect(await screen.findByText(en.ops.priceAtCost(2))).toBeInTheDocument();
    expect(screen.getAllByText(en.ops.atCost)).toHaveLength(2);
    expect(rows()).toEqual(['Dental Bib', 'Bib Holder', 'Cotton Rolls']);

    fireEvent.click(screen.getByText(en.ops.showThem));
    expect(rows()).toEqual(['Dental Bib', 'Cotton Rolls']);
    fireEvent.click(screen.getByText(en.ops.showAll));
    expect(rows()).toHaveLength(3);
  });

  test('no warning when every product sells above cost', async () => {
    mockApi({ get: { '/products': [products[1]], '/categories': [cat], '/admin/stock': [stock[1]] } });
    renderWithProviders(<AdminProducts />);
    await screen.findByText('Bib Holder');
    expect(screen.queryByText(en.ops.atCost)).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
