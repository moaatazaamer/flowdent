import { Link, useNavigate } from 'react-router-dom';
import { useLang } from '../context/LangContext';
import { useCart } from '../context/CartContext';
import { formatPrice, imgFallback, localized, PLACEHOLDER_IMG } from '../utils/format';

export default function Cart() {
  const { t, lang } = useLang();
  const { items, count, total, updateQuantity, removeItem, clearCart } = useCart();
  const navigate = useNavigate();

  if (items.length === 0) {
    return (
      <div className="container page">
        <h1>{t.cart.title}</h1>
        <div className="empty">
          <p>{t.cart.empty}</p>
          <Link to="/products" className="btn btn-primary">{t.cart.continueShopping}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container page">
      <div className="page-head">
        <h1>{t.cart.title}</h1>
        <button className="link-btn" onClick={clearCart}>{t.cart.clear}</button>
      </div>
      <div className="cart-layout">
        <ul className="cart-list">
          {items.map(({ product, quantity }) => (
            <li key={product.id} className="cart-line">
              <img src={product.imageUrl || PLACEHOLDER_IMG} alt="" onError={imgFallback} />
              <div className="cart-line-info">
                <Link to={`/products/${product.id}`} className="product-title">{localized(product, 'name', lang)}</Link>
                <span className="muted small">{formatPrice(product.price, lang)}</span>
                {quantity >= product.stockQuantity && <span className="small warn">{t.cart.stockLimit}</span>}
              </div>
              <div className="qty qty-sm">
                <button onClick={() => updateQuantity(product.id, quantity - 1)} aria-label="-">−</button>
                <input
                  type="number"
                  min="1"
                  value={quantity}
                  onChange={(e) => updateQuantity(product.id, Number(e.target.value) || 1)}
                  aria-label={t.common.quantity}
                />
                <button onClick={() => updateQuantity(product.id, quantity + 1)} aria-label="+">+</button>
              </div>
              <strong className="cart-line-total">{formatPrice(product.price * quantity, lang)}</strong>
              <button className="link-btn danger" onClick={() => removeItem(product.id)}>{t.cart.remove}</button>
            </li>
          ))}
        </ul>
        <aside className="summary card">
          <h3>{t.checkout.summary}</h3>
          <div className="summary-row"><span>{t.cart.itemCount(count)}</span><span /></div>
          <div className="summary-row total"><span>{t.common.total}</span><strong>{formatPrice(total, lang)}</strong></div>
          <button className="btn btn-primary btn-block" onClick={() => navigate('/checkout')}>{t.cart.checkout}</button>
          <Link to="/products" className="link center">{t.cart.continueShopping}</Link>
        </aside>
      </div>
    </div>
  );
}
