import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import { useCart } from '../context/CartContext';
import { formatPrice, imgFallback, localized, PLACEHOLDER_IMG } from '../utils/format';

export default function ProductDetail() {
  const { id } = useParams();
  const { t, lang } = useLang();
  const { addItem, items } = useCart();
  const [product, setProduct] = useState(null);
  const [status, setStatus] = useState('loading');
  const [qty, setQty] = useState(1);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    setStatus('loading');
    api
      .get(`/products/${id}`)
      .then((r) => {
        setProduct(r.data);
        setStatus('ok');
      })
      .catch((e) => setStatus(e?.response?.status === 404 ? 'missing' : 'error'));
  }, [id]);

  if (status === 'loading') return <div className="container page muted">{t.common.loading}</div>;
  if (status !== 'ok') {
    return (
      <div className="container page">
        <div className="empty">{status === 'missing' ? t.products.notFound : t.common.error}</div>
        <Link to="/products" className="link">{t.products.backToProducts}</Link>
      </div>
    );
  }

  const inCart = items.find((i) => i.product.id === product.id)?.quantity || 0;
  const available = Math.max(0, product.stockQuantity - inCart);
  const out = product.stockQuantity <= 0;

  const add = () => {
    addItem(product, qty);
    setAdded(true);
    setQty(1);
    setTimeout(() => setAdded(false), 1500);
  };

  return (
    <div className="container page">
      <Link to={product.category ? `/products?category=${product.category.id}` : '/products'} className="link back-link">
        {t.products.backToProducts}
      </Link>
      <div className="detail">
        <div className="detail-media">
          <img src={product.imageUrl || PLACEHOLDER_IMG} alt={localized(product, 'name', lang)} onError={imgFallback} />
        </div>
        <div className="detail-info">
          {product.category && <span className="eyebrow">{localized(product.category, 'name', lang)}</span>}
          <h1>{localized(product, 'name', lang)}</h1>
          <div className="detail-price">{formatPrice(product.price, lang)}</div>
          <div className={`stock ${out ? 'stock-out' : product.stockQuantity < 10 ? 'stock-low' : 'stock-ok'}`}>
            {out
              ? t.products.outOfStock
              : product.stockQuantity < 10
              ? t.products.lowStock(product.stockQuantity)
              : t.products.inStock(product.stockQuantity)}
          </div>

          <h3 className="detail-sub">{t.products.description}</h3>
          <p className="detail-desc">{localized(product, 'description', lang)}</p>

          {!out && (
            <div className="buy-row">
              <div className="qty">
                <button onClick={() => setQty((q) => Math.max(1, q - 1))} aria-label="-">−</button>
                <input
                  type="number"
                  min="1"
                  max={available || 1}
                  value={qty}
                  onChange={(e) => setQty(Math.max(1, Math.min(Number(e.target.value) || 1, available || 1)))}
                  aria-label={t.common.quantity}
                />
                <button onClick={() => setQty((q) => Math.min(available || 1, q + 1))} aria-label="+">+</button>
              </div>
              <button className="btn btn-primary btn-lg" onClick={add} disabled={available <= 0}>
                {added ? t.products.added : t.products.addToCart}
              </button>
            </div>
          )}
          {!out && available <= 0 && <p className="small muted">{t.cart.stockLimit}</p>}
          {inCart > 0 && (
            <Link to="/cart" className="link">{t.nav.cart} ({inCart})</Link>
          )}
        </div>
      </div>
    </div>
  );
}
