import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import ProductCard from '../components/ProductCard';
import { imgFallback, localized, PLACEHOLDER_IMG } from '../utils/format';

export default function Home() {
  const { t, lang } = useLang();
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    Promise.all([api.get('/categories'), api.get('/products')])
      .then(([c, p]) => {
        setCategories(c.data);
        // Featured: in-stock items, one or two from each category for variety.
        const seen = {};
        const featured = p.data.filter((x) => {
          if (x.stockQuantity <= 0) return false;
          const k = x.category?.id ?? 'none';
          seen[k] = (seen[k] || 0) + 1;
          return seen[k] <= 1;
        });
        setProducts(featured.slice(0, 8));
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <div className="hero-copy">
            <span className="eyebrow eyebrow-light">{t.home.heroEyebrow}</span>
            <h1>{t.home.heroTitle}</h1>
            <p>{t.home.heroText}</p>
            <div className="hero-actions">
              <Link to="/products" className="btn btn-accent btn-lg">{t.home.shopNow}</Link>
              <a href="#categories" className="btn btn-ghost-light btn-lg">{t.home.browseCategories}</a>
            </div>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="hero-blob" />
            <svg viewBox="0 0 200 200" className="hero-tooth">
              <path
                d="M60 40c18-12 30 0 40 0s22-12 40 0c16 12 8 44 0 58-6 14-6 38-16 38-12 0-8-30-24-30s-12 30-24 30c-10 0-10-24-16-38-8-14-16-46 0-58z"
                fill="#fff"
                opacity=".95"
              />
            </svg>
          </div>
        </div>
      </section>

      <section className="container perks">
        {t.home.perks.map((p) => (
          <div key={p.title} className="perk">
            <strong>{p.title}</strong>
            <span className="muted">{p.text}</span>
          </div>
        ))}
      </section>

      {error && <div className="container"><div className="alert alert-error">{t.common.error}</div></div>}

      <section id="categories" className="container section">
        <div className="section-head">
          <h2>{t.home.categoriesTitle}</h2>
          <Link to="/products" className="link">{t.common.viewAll}</Link>
        </div>
        {loading ? (
          <div className="muted">{t.common.loading}</div>
        ) : (
          <div className="category-grid">
            {categories.map((c) => (
              <Link key={c.id} to={`/products?category=${c.id}`} className="category-tile">
                <img src={c.imageUrl || PLACEHOLDER_IMG} alt="" loading="lazy" onError={imgFallback} />
                <div className="category-tile-body">
                  <strong>{localized(c, 'name', lang)}</strong>
                  <span className="small">{localized(c, 'description', lang)}</span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="container section">
        <div className="section-head">
          <h2>{t.home.featuredTitle}</h2>
          <Link to="/products" className="link">{t.common.viewAll}</Link>
        </div>
        <div className="product-grid">
          {products.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>
    </>
  );
}
