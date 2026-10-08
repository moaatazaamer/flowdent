import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const STORAGE_KEY = 'flowdent_cart';
const CartContext = createContext(null);

function readCart() {
  try {
    const items = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(items) ? items : [];
  } catch {
    return [];
  }
}

/** Cart lines keep a snapshot of the product so the cart renders without refetching. */
export function CartProvider({ children }) {
  const [items, setItems] = useState(readCart);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      /* ignore */
    }
  }, [items]);

  const addItem = useCallback((product, qty = 1) => {
    setItems((cur) => {
      const max = product.stockQuantity ?? Infinity;
      const existing = cur.find((i) => i.product.id === product.id);
      if (existing) {
        return cur.map((i) =>
          i.product.id === product.id
            ? { product, quantity: Math.min(i.quantity + qty, max) }
            : i
        );
      }
      return [...cur, { product, quantity: Math.min(qty, max) }];
    });
  }, []);

  const updateQuantity = useCallback((productId, qty) => {
    setItems((cur) =>
      cur
        .map((i) => {
          if (i.product.id !== productId) return i;
          const max = i.product.stockQuantity ?? Infinity;
          return { ...i, quantity: Math.max(0, Math.min(qty, max)) };
        })
        .filter((i) => i.quantity > 0)
    );
  }, []);

  const removeItem = useCallback((productId) => {
    setItems((cur) => cur.filter((i) => i.product.id !== productId));
  }, []);

  const clearCart = useCallback(() => setItems([]), []);

  const value = useMemo(() => {
    const count = items.reduce((s, i) => s + i.quantity, 0);
    const total = items.reduce((s, i) => s + Number(i.product.price) * i.quantity, 0);
    return { items, count, total, addItem, updateQuantity, removeItem, clearCart };
  }, [items, addItem, updateQuantity, removeItem, clearCart]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
