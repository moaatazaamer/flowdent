import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import ProductCard from '../components/ProductCard';
import ContactStrip from '../components/ContactStrip';
import { OraLogo } from '../components/Logo';
import { isOwnBrand } from '../config/company';
import { imgFallback, localized, PLACEHOLDER_IMG } from '../utils/format';

/** The three small stars from the logo. */
export function Sparkles({ size = 22 }) {
  return (
    <svg className="sparkles" width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15 2l1.3 4.2L20.5 7.5l-4.2 1.3L15 13l-1.3-4.2-4.2-1.3 4.2-1.3z" />
      <path d="M6 10l.8 2.2 2.2.8-2.2.8L6 16l-.8-2.2-2.2-.8 2.2-.8z" />
      <path d="M19.5 15l.6 1.6 1.6.6-1.6.6-.6 1.6-.6-1.6-1.6-.6 1.6-.6z" />
    </svg>
  );
}

export default function Home() {
  const { t, lang } = useLang();
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [oraProducts, setOraProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    Promise.all([api.get('/categories'), api.get('/products')])
      .then(([c, p]) => {
        setCategories(c.data);
        // In-stock items first; out-of-stock ones still show (marked) so the catalogue is visible.
        const all = [...p.data].sort((a, b) => (b.stockQuantity > 0) - (a.stockQuantity > 0));
        setOraProducts(all.filter(isOwnBrand).slice(0, 4));
        // Popular: one item from each category, for variety.
        const seen = {};
        setProducts(all.filter((x) => {
          const k = x.category?.id ?? 'none';
          seen[k] = (seen[k] || 0) + 1;
          return seen[k] <= 1;
        }).slice(0, 8));
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <section className="hero">
        <div className="hero-rings" aria-hidden="true">
          <span className="ring ring-1" />
          <span className="ring ring-2" />
          <span className="ring ring-3" />
        </div>
        <div className="container hero-inner">
          <div className="hero-copy">
            <h1>{t.home.heroTitle}</h1>
            <p className="hero-lead">{t.home.heroText}</p>
            <div className="hero-actions">
              <Link to="/products" className="btn btn-primary btn-lg">{t.home.shopNow}</Link>
              <a href="#categories" className="btn btn-outline btn-lg">{t.home.browseCategories}</a>
            </div>
            <p className="hero-tagline"><Sparkles /> {t.tagline}</p>
          </div>
          <Link to="/ora" className="hero-agent">
            <span className="hero-agent-label">{t.home.ownBrand}</span>
            <OraLogo height={78} />
            <span className="hero-agent-cta">{t.home.oraCta}</span>
          </Link>
        </div>
        <div className="container hero-contact">
          <ContactStrip />
        </div>
      </section>

      {error && <div className="container"><div className="alert alert-error">{t.common.error}</div></div>}

      {oraProducts.length > 0 && (
        <section className="ora-band">
          <div className="container">
            <div className="ora-band-head">
              <OraLogo height={40} />
              <div>
                <h2>{t.home.oraTitle}</h2>
                <p className="muted">{t.home.oraText}</p>
              </div>
              <Link to="/ora" className="btn btn-ora">{t.home.oraCta}</Link>
            </div>
            <div className="product-grid">
              {oraProducts.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          </div>
        </section>
      )}

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
