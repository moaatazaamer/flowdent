import { fireEvent, screen } from '@testing-library/react';
import { Route, useLocation } from 'react-router-dom';
import Products from './Products';
import en from '../i18n/en';
import { mockApi, renderWithProviders } from '../test-utils';

const cats = [{ id: 1, name: 'Restorative', nameAr: 'ترميم' }, { id: 2, name: 'Gloves', nameAr: 'قفازات' }];
const items = [
  { id: 1, name: 'Composite A2', price: 845, stockQuantity: 5, brand: 'Ora', category: cats[0] },
  { id: 2, name: 'Bonding Agent', price: 1225, stockQuantity: 5, brand: 'Ora', category: cats[0] },
  { id: 3, name: 'Nitrile Gloves', price: 495, stockQuantity: 5, brand: null, category: cats[1] },
  { id: 4, name: 'Acme Etchant', price: 300, stockQuantity: 5, brand: 'Acme', category: cats[0] },
];

function Where() {
  const loc = useLocation();
  return <output data-testid="where">{loc.pathname + loc.search}</output>;
}

function renderProducts(route) {
  mockApi({ get: { '/categories': cats, '/products': items, '/products/category/1': items.filter((p) => p.category.id === 1) } });
  return renderWithProviders(<><Products /><Where /></>, { route, path: '/products' });
}

describe('Products page brands', () => {
  test('?brand=Ora shows only Ora products with the Ora heading', async () => {
    renderProducts('/products?brand=Ora');
    expect(await screen.findByText('Composite A2')).toBeInTheDocument();
    expect(screen.getByText('Bonding Agent')).toBeInTheDocument();
    expect(screen.queryByText('Nitrile Gloves')).not.toBeInTheDocument();
    expect(screen.queryByText('Acme Etchant')).not.toBeInTheDocument();
    expect(screen.getByText(en.home.oraTitle, { selector: 'h1' })).toBeInTheDocument();
    expect(screen.getByText(en.home.oraCta)).toHaveAttribute('href', '/ora');
  });

  test('brand chips list each brand once and switch the filter', async () => {
    renderProducts('/products');
    await screen.findByText('Nitrile Gloves');
    expect(screen.getAllByLabelText('Ora')).toHaveLength(1);      // chip (Ora logo inside)
    fireEvent.click(screen.getByLabelText('Acme'));
    expect(screen.getByTestId('where')).toHaveTextContent('/products?brand=Acme');
    expect(screen.getByText('Acme Etchant')).toBeInTheDocument();
    expect(screen.queryByText('Composite A2')).not.toBeInTheDocument();

    fireEvent.click(screen.getByText(en.products.allBrands));
    expect(screen.getByText('Composite A2')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent(/^\/products$/);
  });

  test('category and brand filters combine', async () => {
    renderProducts('/products?brand=Ora');
    await screen.findByText('Composite A2');
    fireEvent.click(screen.getByText('Restorative', { selector: 'button' }));
    expect(screen.getByTestId('where')).toHaveTextContent('category=1');
    expect(screen.getByTestId('where')).toHaveTextContent('brand=Ora');
    expect(await screen.findByText('Bonding Agent')).toBeInTheDocument();
    expect(screen.queryByText('Acme Etchant')).not.toBeInTheDocument();
  });
});
