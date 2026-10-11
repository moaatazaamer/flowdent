import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import Login from './Login';
import en from '../i18n/en';
import { apiError, mockApi, renderWithProviders } from '../test-utils';

function renderLogin() {
  return renderWithProviders(<Login />, {
    route: '/login',
    path: '/login',
    extraRoutes: [
      <Route key="a" path="/admin" element={<div>ADMIN HOME</div>} />,
      <Route key="h" path="/" element={<div>SHOP HOME</div>} />,
    ],
  });
}

function fillAndSubmit(email, password) {
  fireEvent.change(screen.getByLabelText(en.auth.emailOrUser), { target: { value: email } });
  fireEvent.change(screen.getByLabelText(en.auth.password), { target: { value: password } });
  fireEvent.click(screen.getByText(en.auth.signIn, { selector: 'button' }));
}

describe('Login page', () => {
  test('admin login goes to the admin panel and stores the token', async () => {
    const api = mockApi({
      post: { '/auth/login': { token: 'jwt-123', user: { id: 1, email: 'admin', role: 'ADMIN', fullName: 'Admin' } } },
    });
    renderLogin();
    fillAndSubmit(' admin ', 'admin');

    expect(await screen.findByText('ADMIN HOME')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'admin', password: 'admin' });
    expect(localStorage.getItem('flowdent_token')).toBe('jwt-123');
  });

  test('customers go to the shop home page', async () => {
    mockApi({ post: { '/auth/login': { token: 't', user: { id: 2, email: 'dr@x.com', role: 'CUSTOMER' } } } });
    renderLogin();
    fillAndSubmit('dr@x.com', 'secret1');
    expect(await screen.findByText('SHOP HOME')).toBeInTheDocument();
  });

  test('wrong password shows a friendly error and stays on the page', async () => {
    mockApi({ post: { '/auth/login': apiError(401, 'Invalid email or password') } });
    renderLogin();
    fillAndSubmit('admin', 'wrong');

    expect(await screen.findByText(en.auth.invalid)).toBeInTheDocument();
    expect(screen.queryByText('ADMIN HOME')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(en.auth.signIn, { selector: 'button' })).toBeEnabled());
    expect(localStorage.getItem('flowdent_token')).toBeNull();
  });
});
