import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { useLang } from '../context/LangContext';
import StatusBadge from '../components/StatusBadge';
import { formatDate, formatPrice, localized } from '../utils/format';

export default function Orders() {
  const { t, lang } = useLang();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    api
      .get('/orders/my')
      .then((r) => setOrders(r.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="container page">
      <h1>{t.orders.title}</h1>
      {error && <div className="alert alert-error">{t.common.error}</div>}
      {loading ? (
        <div className="muted">{t.common.loading}</div>
      ) : orders.length === 0 ? (
        <div className="empty">
          <p>{t.orders.none}</p>
          <Link to="/products" className="btn btn-primary">{t.cart.continueShopping}</Link>
        </div>
      ) : (
        <div className="order-list">
          {orders.map((o) => (
            <article key={o.id} className="card order-card">
              <header className="order-head">
                <div>
                  <strong>{t.orders.order} #{o.id}</strong>
                  <div className="small muted">{t.orders.placedOn} {formatDate(o.createdAt, lang, true)}</div>
                </div>
                <div className="order-badges">
                  <StatusBadge status={o.status} />
                  <span className={`pill ${o.paid ? 'pill-ok' : 'pill-muted'}`}>{o.paid ? t.orders.paid : t.orders.unpaid}</span>
                </div>
              </header>
              <ul className="order-items">
                {o.items.map((it) => (
                  <li key={it.id}>
                    <span>{it.product ? localized(it.product, 'name', lang) : '—'} <span className="muted">× {it.quantity}</span></span>
                    <span>{formatPrice(it.price * it.quantity, lang)}</span>
                  </li>
                ))}
              </ul>
              <footer className="order-foot">
                <span className="small muted">
                  {t.deliveryMethod[o.deliveryMethod]} · {t.paymentMethod[o.paymentMethod]}
                  {o.deliveryMethod === 'HOME_DELIVERY' && o.customerDetails?.deliveryAddress
                    ? ` · ${o.customerDetails.deliveryAddress}`
                    : ''}
                </span>
                <strong>{formatPrice(o.totalAmount, lang)}</strong>
              </footer>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
