import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useLang } from '../context/LangContext';
import { useCart } from '../context/CartContext';
import { formatPrice, imgFallback, localized, PLACEHOLDER_IMG } from '../utils/format';
import { BrandBadge } from './Logo';

export default function ProductCard({ product }) {
  const { t, lang } = useLang();
  const { addItem } = useCart();
  const [added, setAdded] = useState(false);
  const out = !product.stockQuantity || product.stockQuantity <= 0;

  const add = () => {
    addItem(product, 1);
    setAdded(true);
    setTimeout(() => setAdded(false), 1200);
  };

  return (
    <article className="product-card">
      <Link to={`/products/${product.id}`} className="product-media">
        <img src={product.imageUrl || PLACEHOLDER_IMG} alt="" loading="lazy" onError={imgFallback} />
        {out && <span className="ribbon">{t.products.outOfStock}</span>}
        <BrandBadge brand={product.brand} className="on-media" />
      </Link>
      <div className="product-body">
        {product.category && <span className="product-category">{localized(product.category, 'name', lang)}</span>}
        <Link to={`/products/${product.id}`} className="product-title">
          {localized(product, 'name', lang)}
        </Link>
        <div className="product-foot">
          <span className="price">{formatPrice(product.price, lang)}</span>
          <button className="btn btn-sm btn-primary" disabled={out} onClick={add}>
            {added ? t.products.added : t.products.addToCart}
          </button>
        </div>
      </div>
    </article>
  );
}
