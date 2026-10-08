import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import ProductCard from '../components/ProductCard';

export default function Search() {
  const { t } = useLang();
  const [params] = useSearchParams();
  const q = (params.get('q') || '').trim();
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!q) {
      setResults([]);
      return;
    }
    setLoading(true);
    setError(false);
    api
      .get('/products/search', { params: { q } })
      .then((r) => setResults(r.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [q]);

  return (
    <div className="container page">
      <div className="page-head">
        <h1>{q ? t.search.resultsFor(q) : t.search.title}</h1>
      </div>
      {error && <div className="alert alert-error">{t.common.error}</div>}
      {!q ? (
        <div className="empty">{t.search.prompt}</div>
      ) : loading ? (
        <div className="muted">{t.common.loading}</div>
      ) : results.length === 0 ? (
        <div className="empty">{t.search.none}</div>
      ) : (
        <>
          <p className="muted small">{t.products.count(results.length)}</p>
          <div className="product-grid">
            {results.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </>
      )}
    </div>
  );
}
