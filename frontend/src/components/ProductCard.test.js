import { fireEvent, screen } from '@testing-library/react';
import ProductCard from './ProductCard';
import { useCart } from '../context/CartContext';
import { renderWithProviders } from '../test-utils';

function CartCount() {
  const { count, total } = useCart();
  return <div data-testid="cart">{count} / {total}</div>;
}

const product = {
  id: 7, name: 'Composite A2', nameAr: 'كمبوزيت A2', price: 845, stockQuantity: 3,
  imageUrl: null, category: { id: 2, name: 'Restorative', nameAr: 'مواد الترميم' },
};

describe('ProductCard', () => {
  test('shows name, category and EGP price, and adds to the cart', () => {
    renderWithProviders(<><ProductCard product={product} /><CartCount /></>);

    expect(screen.getByText('Composite A2')).toBeInTheDocument();
    expect(screen.getByText('Restorative')).toBeInTheDocument();
    expect(screen.getByText(/EGP\s?845\.00/)).toBeInTheDocument();

    fireEvent.click(screen.getByText('Add to cart'));
    expect(screen.getByText('Added ✓')).toBeInTheDocument();
    expect(screen.getByTestId('cart')).toHaveTextContent('1 / 845');
  });

  test('shows the Arabic name when Arabic is selected', () => {
    localStorage.setItem('flowdent_lang', 'ar');
    renderWithProviders(<ProductCard product={product} />);
    expect(screen.getByText('كمبوزيت A2')).toBeInTheDocument();
    expect(screen.getByText('مواد الترميم')).toBeInTheDocument();
  });

  test('out-of-stock products cannot be added', () => {
    renderWithProviders(<><ProductCard product={{ ...product, stockQuantity: 0 }} /><CartCount /></>);
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
    const button = screen.getByText('Add to cart');
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(screen.getByTestId('cart')).toHaveTextContent('0 / 0');
  });
});
