import { fireEvent, screen, waitFor } from '@testing-library/react';
import Checkout from './Checkout';
import en from '../i18n/en';
import { apiError, mockApi, renderWithProviders } from '../test-utils';

const gloves = { id: 1, name: 'Nitrile Gloves', nameAr: 'قفازات', price: 150, stockQuantity: 10 };
const bond = { id: 2, name: 'Bonding Agent', nameAr: 'مادة ربط', price: 1225, stockQuantity: 4 };

function seedCart() {
  localStorage.setItem('flowdent_cart', JSON.stringify([
    { product: gloves, quantity: 2 },
    { product: bond, quantity: 1 },
  ]));
}

function fillContact() {
  fireEvent.change(screen.getByLabelText(en.checkout.fullName), { target: { value: 'Dr. Sara Mahmoud' } });
  fireEvent.change(screen.getByLabelText(en.checkout.phone), { target: { value: '+20 100 111 2233' } });
  fireEvent.change(screen.getByLabelText(en.checkout.email), { target: { value: 'sara@clinic.example' } });
}

describe('Checkout (guest)', () => {
  test('home delivery, cash on delivery: sends the order and shows the confirmation', async () => {
    seedCart();
    const api = mockApi({
      get: { '/payment/config': { enabled: false, currency: 'egp' } },
      post: { '/orders/guest': { id: 42, totalAmount: 1525, paid: false } },
    });
    renderWithProviders(<Checkout />);

    expect(screen.getByText(/EGP\s?1,525\.00/)).toBeInTheDocument();
    fillContact();
    fireEvent.change(screen.getByLabelText(en.checkout.address), { target: { value: '12 Mostafa El Nahas, Nasr City' } });
    fireEvent.click(screen.getByText(en.checkout.placeOrder));

    expect(await screen.findByText(en.checkout.successTitle)).toBeInTheDocument();
    expect(screen.getByText(en.checkout.successText(42))).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/orders/guest', {
      items: [{ productId: 1, quantity: 2 }, { productId: 2, quantity: 1 }],
      paymentMethod: 'CASH_ON_DELIVERY',
      deliveryMethod: 'HOME_DELIVERY',
      customerDetails: {
        fullName: 'Dr. Sara Mahmoud',
        email: 'sara@clinic.example',
        phone: '+20 100 111 2233',
        deliveryAddress: '12 Mostafa El Nahas, Nasr City',
      },
    });
    // The cart is emptied and the empty cart is saved (persisted by an effect after render).
    await waitFor(() => expect(JSON.parse(localStorage.getItem('flowdent_cart'))).toEqual([]));
  });

  test('shop pickup needs no address and relabels payment options', async () => {
    seedCart();
    const api = mockApi({
      get: { '/payment/config': { enabled: false } },
      post: { '/orders/guest': { id: 43, totalAmount: 1525 } },
    });
    renderWithProviders(<Checkout />);

    fireEvent.click(screen.getByLabelText(new RegExp(`^${en.checkout.shopPickup}`)));
    expect(screen.queryByLabelText(en.checkout.address)).not.toBeInTheDocument();
    expect(screen.getByText(en.checkout.codPickup)).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(new RegExp(`^${en.checkout.posPickup}`)));
    fillContact();
    fireEvent.click(screen.getByText(en.checkout.placeOrder));

    await screen.findByText(en.checkout.successTitle);
    const body = api.post.mock.calls[0][1];
    expect(body.deliveryMethod).toBe('SHOP_PICKUP');
    expect(body.paymentMethod).toBe('POS_ON_DELIVERY');
    expect(body.customerDetails.deliveryAddress).toBe('');
  });

  test('online card payment is disabled when Stripe is not configured', async () => {
    seedCart();
    mockApi({ get: { '/payment/config': { enabled: false } } });
    renderWithProviders(<Checkout />);

    const online = screen.getByLabelText(new RegExp(`^${en.checkout.online}`));
    expect(online).toBeDisabled();
    expect(await screen.findByText(en.checkout.onlineUnavailable)).toBeInTheDocument();
  });

  test('a stock error from the server is shown and the cart is kept', async () => {
    seedCart();
    mockApi({
      get: { '/payment/config': { enabled: false } },
      post: { '/orders/guest': apiError(409, 'Insufficient stock for "Bonding Agent" (available: 0)') },
    });
    renderWithProviders(<Checkout />);
    fillContact();
    fireEvent.change(screen.getByLabelText(en.checkout.address), { target: { value: 'Giza' } });
    fireEvent.click(screen.getByText(en.checkout.placeOrder));

    expect(await screen.findByText(/Insufficient stock for "Bonding Agent"/)).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem('flowdent_cart'))).toHaveLength(2);
    await waitFor(() => expect(screen.getByText(en.checkout.placeOrder)).toBeEnabled());
  });

  test('an empty cart cannot be checked out', () => {
    mockApi({ get: { '/payment/config': { enabled: false } } });
    renderWithProviders(<Checkout />);
    expect(screen.getByText(en.checkout.emptyCart)).toBeInTheDocument();
    expect(screen.queryByText(en.checkout.placeOrder)).not.toBeInTheDocument();
  });
});
