import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import ProductCard from '../components/ProductCard';
import { localized } from '../utils/format';
import { BrandBadge } from '../components/Logo';
import { COMPANY } from '../config/company';

export default function Products() {
  const { t, lang } = useLang();
  const [params, setParams] = useSearchParams();
  const categoryId = params.get('category') || '';
  const brand = params.get('brand') || '';
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [sort, setSort] = useState('default');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    api.get('/categories').then((r) => setCategories(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(false);
    api
      .get(categoryId ? `/products/category/${categoryId}` : '/products')
      .then((r) => setProducts(r.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [categoryId]);

  const brands = useMemo(
    () => [...new Set(products.map((p) => p.brand).filter(Boolean))].sort((a, b) => a.localeCompare(b)),
    [products]
  );

  const sorted = useMemo(() => {
    const list = products.filter((p) => !brand || (p.brand || '').toLowerCase() === brand.toLowerCase());
    if (sort === 'priceAsc') list.sort((a, b) => a.price - b.price);
    if (sort === 'priceDesc') list.sort((a, b) => b.price - a.price);
    if (sort === 'name') list.sort((a, b) => localized(a, 'name', lang).localeCompare(localized(b, 'name', lang), lang));
    return list;
  }, [products, sort, lang, brand]);

  const current = categories.find((c) => String(c.id) === categoryId);
  const update = (next) => {
    const merged = { category: categoryId, brand, ...next };
    setParams(Object.fromEntries(Object.entries(merged).filter(([, v]) => v)));
  };
  const selectCategory = (id) => update({ category: id });
  const selectBrand = (b) => update({ brand: b });
  const isAgentBrand = brand.toLowerCase() === COMPANY.agentBrand.toLowerCase();
  const title = current
    ? localized(current, 'name', lang)
    : brand ? (isAgentBrand ? t.home.oraTitle : brand) : t.products.title;

  return (
    <div className="container page">
      <div className="page-head">
        <div>
          <h1>{title}</h1>
          {current && <p className="muted">{localized(current, 'description', lang)}</p>}
          {!current && isAgentBrand && <p className="muted">{t.home.oraText}</p>}
        </div>
        <label className="inline-field">
          <span>{t.products.sortBy}</span>
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="default">{t.products.sortDefault}</option>
            <option value="priceAsc">{t.products.sortPriceAsc}</option>
            <option value="priceDesc">{t.products.sortPriceDesc}</option>
            <option value="name">{t.products.sortName}</option>
          </select>
        </label>
      </div>

      <div className="chips" role="tablist" aria-label={t.products.filterByCategory}>
        <button className={`chip ${!categoryId ? 'active' : ''}`} onClick={() => selectCategory('')}>
          {t.products.allCategories}
        </button>
        {categories.map((c) => (
          <button
            key={c.id}
            className={`chip ${String(c.id) === categoryId ? 'active' : ''}`}
            onClick={() => selectCategory(String(c.id))}
          >
            {localized(c, 'name', lang)}
          </button>
        ))}
      </div>

      {brands.length > 0 && (
        <div className="chips chips-brands" role="tablist" aria-label={t.products.brand}>
          <button className={`chip ${!brand ? 'active' : ''}`} onClick={() => selectBrand('')}>
            {t.products.allBrands}
          </button>
          {brands.map((b) => (
            <button
              key={b}
              className={`chip chip-brand ${brand.toLowerCase() === b.toLowerCase() ? 'active' : ''}`}
              onClick={() => selectBrand(b)}
              aria-label={b}
            >
              <BrandBadge brand={b} />
            </button>
          ))}
        </div>
      )}

      {error && <div className="alert alert-error">{t.common.error}</div>}
      {loading ? (
        <div className="muted">{t.common.loading}</div>
      ) : (
        <>
          <p className="muted small">{t.products.count(sorted.length)}</p>
          {sorted.length === 0 ? (
            <div className="empty">{t.common.noResults}</div>
          ) : (
            <div className="product-grid">
              {sorted.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}
        </>
      )}
    </div>
  );
}
