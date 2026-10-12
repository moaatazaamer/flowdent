import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import ProductCard from '../components/ProductCard';
import ContactStrip from '../components/ContactStrip';
import { OraLogo } from '../components/Logo';
import { isOwnBrand } from '../config/company';
import { localized } from '../utils/format';
import { Sparkles } from './Home';

/** In-stock products first, then by name. */
const inStockFirst = (lang) => (a, b) =>
  (b.stockQuantity > 0) - (a.stockQuantity > 0) ||
  localized(a, 'name', lang).localeCompare(localized(b, 'name', lang), lang);

/** Landing page for Ora, Flow Dent's own brand. */
export default function Ora() {
  const { t, lang } = useLang();
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    api.get('/products')
      .then((r) => setProducts(r.data.filter(isOwnBrand)))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  // Only the categories that actually contain Ora products.
  const categories = useMemo(() => {
    const byId = new Map();
    products.forEach((p) => p.category && byId.set(p.category.id, p.category));
    return [...byId.values()].sort((a, b) => localized(a, 'name', lang).localeCompare(localized(b, 'name', lang), lang));
  }, [products, lang]);

  const shown = useMemo(
    () => products.filter((p) => !category || String(p.category?.id) === category).sort(inStockFirst(lang)),
    [products, category, lang]
  );

  return (
    <>
      <section className="ora-hero">
        <div className="hero-rings" aria-hidden="true">
          <span className="ring ring-1" />
          <span className="ring ring-2" />
          <span className="ring ring-3" />
        </div>
        <div className="container ora-hero-inner">
          <div className="ora-hero-copy">
            <p className="ora-eyebrow"><Sparkles size={18} /> {t.ora.eyebrow}</p>
            <h1>{t.ora.title}</h1>
            <p className="ora-hero-lead">{t.ora.text}</p>
            <a href="#ora-range" className="btn btn-lg ora-hero-btn">{t.ora.shopRange}</a>
          </div>
          <div className="ora-hero-mark">
            <OraLogo height={120} variant="white" />
          </div>
        </div>
      </section>

      <section className="container ora-points">
        {t.ora.points.map((pt) => (
          <div key={pt.title} className="ora-point">
            <strong>{pt.title}</strong>
            <span>{pt.text}</span>
          </div>
        ))}
      </section>

      <section id="ora-range" className="container section">
        <div className="section-head">
          <h2>{t.ora.rangeTitle}</h2>
          {!loading && !error && <span className="muted">{t.ora.count(shown.length)}</span>}
        </div>

        {categories.length > 1 && (
          <div className="chips" aria-label={t.products.filterByCategory}>
            <button className={`chip ${!category ? 'active' : ''}`} onClick={() => setCategory('')}>{t.ora.all}</button>
            {categories.map((c) => (
              <button
                key={c.id}
                className={`chip ${category === String(c.id) ? 'active' : ''}`}
                onClick={() => setCategory(String(c.id))}
              >
                {localized(c, 'name', lang)}
              </button>
            ))}
          </div>
        )}

        {error && <div className="alert alert-error">{t.common.error}</div>}
        {loading ? (
          <div className="muted">{t.common.loading}</div>
        ) : !error && shown.length === 0 ? (
          <p className="muted">{t.ora.empty}</p>
        ) : (
          <div className="product-grid">
            {shown.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        )}
      </section>

      <section className="ora-contact">
        <div className="container">
          <div className="ora-contact-head">
            <div>
              <h2>{t.ora.bulkTitle}</h2>
              <p className="muted">{t.ora.bulkText}</p>
            </div>
            <Link to="/products" className="link">{t.ora.shopAll}</Link>
          </div>
          <ContactStrip />
        </div>
      </section>
    </>
  );
}
