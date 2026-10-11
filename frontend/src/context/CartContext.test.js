import { act, renderHook } from '@testing-library/react';
import { CartProvider, useCart } from './CartContext';

const gloves = { id: 1, name: 'Gloves', price: 150, stockQuantity: 5 };
const mirror = { id: 2, name: 'Mirror', price: 40.5, stockQuantity: 100 };

const wrapper = ({ children }) => <CartProvider>{children}</CartProvider>;

describe('CartContext', () => {
  test('starts empty', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    expect(result.current.items).toEqual([]);
    expect(result.current.count).toBe(0);
    expect(result.current.total).toBe(0);
  });

  test('adding the same product twice merges the line', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    act(() => result.current.addItem(gloves, 1));
    act(() => result.current.addItem(gloves, 2));
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(3);
    expect(result.current.count).toBe(3);
    expect(result.current.total).toBe(450);
  });

  test('quantity never exceeds available stock', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    act(() => result.current.addItem(gloves, 4));
    act(() => result.current.addItem(gloves, 4));
    expect(result.current.items[0].quantity).toBe(5);
    act(() => result.current.updateQuantity(1, 99));
    expect(result.current.items[0].quantity).toBe(5);
  });

  test('setting quantity to zero removes the line; totals add up across lines', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    act(() => {
      result.current.addItem(gloves, 2);
      result.current.addItem(mirror, 2);
    });
    expect(result.current.total).toBe(381);
    act(() => result.current.updateQuantity(1, 0));
    expect(result.current.items.map((i) => i.product.id)).toEqual([2]);
    act(() => result.current.removeItem(2));
    expect(result.current.count).toBe(0);
  });

  test('cart survives a reload (saved in localStorage)', () => {
    const first = renderHook(() => useCart(), { wrapper });
    act(() => first.result.current.addItem(mirror, 3));
    first.unmount();

    expect(JSON.parse(localStorage.getItem('flowdent_cart'))).toHaveLength(1);
    const second = renderHook(() => useCart(), { wrapper });
    expect(second.result.current.count).toBe(3);
    act(() => second.result.current.clearCart());
    expect(JSON.parse(localStorage.getItem('flowdent_cart'))).toEqual([]);
  });

  test('a corrupted saved cart is ignored', () => {
    localStorage.setItem('flowdent_cart', '{not json');
    const { result } = renderHook(() => useCart(), { wrapper });
    expect(result.current.items).toEqual([]);
  });
});
