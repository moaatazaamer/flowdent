/* Helpers shared by the *.test.js files (not part of the app bundle). */
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import api from './api';
import { LangProvider } from './context/LangContext';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';

/** Renders UI inside the same providers the app uses, on an in-memory route. */
export function renderWithProviders(ui, { route = '/', path = '*', extraRoutes = null } = {}) {
  return render(
    <LangProvider>
      <AuthProvider>
        <CartProvider>
          <MemoryRouter initialEntries={[route]}>
            <Routes>
              <Route path={path} element={ui} />
              {extraRoutes}
            </Routes>
          </MemoryRouter>
        </CartProvider>
      </AuthProvider>
    </LangProvider>
  );
}

/** Error shaped like an axios error with an API JSON body. */
export function apiError(status, message) {
  return Object.assign(new Error(message), { response: { status, data: { message } } });
}

/**
 * Replaces the axios instance's methods with fakes.
 * routes = { get: { '/url': data | (url, config) => data | Error }, post: {...}, put: {...}, delete: {...} }
 * Unknown GETs resolve to [] and other unknown calls to {}.
 */
export function mockApi(routes = {}) {
  const handler = (method) => (url, ...rest) => {
    const table = routes[method] || {};
    let value = table[url];
    if (typeof value === 'function') value = value(url, ...rest);
    if (value instanceof Error) return Promise.reject(value);
    if (value === undefined) value = method === 'get' ? [] : {};
    return Promise.resolve({ data: value });
  };
  return {
    get: jest.spyOn(api, 'get').mockImplementation(handler('get')),
    post: jest.spyOn(api, 'post').mockImplementation(handler('post')),
    put: jest.spyOn(api, 'put').mockImplementation(handler('put')),
    delete: jest.spyOn(api, 'delete').mockImplementation(handler('delete')),
  };
}

export const today = () => {
  const d = new Date();
  const pad = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

/** A product row as returned by GET /api/admin/stock. */
export function stockRow(overrides = {}) {
  return {
    id: 10, name: 'Nitrile Gloves M', nameAr: 'قفازات نتريل', sku: 'GLO-001', imageUrl: null,
    categoryId: 1, categoryName: 'Gloves', categoryNameAr: 'القفازات',
    price: 150, costPrice: 95, marginPercent: 36.7, stockQuantity: 50, reorderLevel: 10,
    onOrder: 0, stockValue: 4750, stockStatus: 'OK',
    ...overrides,
  };
}
