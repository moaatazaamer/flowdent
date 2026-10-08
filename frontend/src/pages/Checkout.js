import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errorMessage } from '../api';
import { useLang } from '../context/LangContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import StripePaymentForm from '../components/StripePaymentForm';
import { formatPrice, localized } from '../utils/format';

function formatAddress(a) {
  return [a.fullAddress, a.city, a.postalCode].filter(Boolean).join(', ');
}

function Option({ name, value, checked, onChange, title, text, disabled }) {
  return (
    <label className={`option ${checked ? 'checked' : ''} ${disabled ? 'disabled' : ''}`}>
      <input type="radio" name={name} value={value} checked={checked} onChange={onChange} disabled={disabled} />
      <span>
        <strong>{title}</strong>
        <span className="small muted">{text}</span>
      </span>
    </label>
  );
}

export default function Checkout() {
  const { t, lang } = useLang();
  const { user, isAuthenticated } = useAuth();
  const { items, total, clearCart } = useCart();

  const [contact, setContact] = useState({
    fullName: user?.fullName || '',
    email: user?.email?.includes('@') ? user.email : '',
    phone: user?.phone || '',
  });
  const [deliveryMethod, setDeliveryMethod] = useState('HOME_DELIVERY');
  const [paymentMethod, setPaymentMethod] = useState('CASH_ON_DELIVERY');
  const [addresses, setAddresses] = useState([]);
  const [addressChoice, setAddressChoice] = useState('new');
  const [newAddress, setNewAddress] = useState(user?.address || '');
  const [payConfig, setPayConfig] = useState({ enabled: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [order, setOrder] = useState(null);
  const [stage, setStage] = useState('form'); // form | pay | done

  useEffect(() => {
    api.get('/payment/config').then((r) => setPayConfig(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    api
      .get('/profile/addresses')
      .then((r) => {
        setAddresses(r.data);
        const def = r.data.find((a) => a.isDefault) || r.data[0];
        if (def) setAddressChoice(String(def.id));
      })
      .catch(() => {});
  }, [isAuthenticated]);

  const pickup = deliveryMethod === 'SHOP_PICKUP';
  const deliveryAddress = pickup
    ? ''
    : addressChoice === 'new'
    ? newAddress.trim()
    : formatAddress(addresses.find((a) => String(a.id) === addressChoice) || {});

  const placeOrder = async (e) => {
    e.preventDefault();
    setError('');
    if (!pickup && !deliveryAddress) {
      setError(t.checkout.address + ' — ' + t.common.required);
      return;
    }
    setBusy(true);
    try {
      const payload = {
        items: items.map((i) => ({ productId: i.product.id, quantity: i.quantity })),
        paymentMethod,
        deliveryMethod,
        customerDetails: { ...contact, deliveryAddress },
      };
      const res = await api.post(isAuthenticated ? '/orders' : '/orders/guest', payload);
      setOrder(res.data);
      clearCart();
      setStage(paymentMethod === 'ONLINE_PAYMENT' ? 'pay' : 'done');
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  if (stage === 'done' && order) {
    return (
      <div className="container page narrow">
        <div className="card success-card">
          <div className="success-icon" aria-hidden="true">✓</div>
          <h1>{t.checkout.successTitle}</h1>
          <p>{t.checkout.successText(order.id)}</p>
          {order.paid && <p className="pill pill-ok">{t.checkout.paidText}</p>}
          <div className="summary-row total"><span>{t.common.total}</span><strong>{formatPrice(order.totalAmount, lang)}</strong></div>
          <div className="row-actions center">
            {isAuthenticated && <Link to="/orders" className="btn btn-primary">{t.checkout.viewOrders}</Link>}
            <Link to="/products" className="btn btn-outline">{t.cart.continueShopping}</Link>
          </div>
        </div>
      </div>
    );
  }

  if (stage === 'pay' && order) {
    return (
      <div className="container page narrow">
        <h1>{t.checkout.payNow}</h1>
        <div className="card">
          <p className="muted">{t.checkout.successText(order.id)}</p>
          <StripePaymentForm
            orderId={order.id}
            publishableKey={payConfig.publishableKey}
            billing={contact}
            onSuccess={(paidOrder) => {
              setOrder(paidOrder);
              setStage('done');
            }}
          />
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="container page">
        <h1>{t.checkout.title}</h1>
        <div className="empty">
          <p>{t.checkout.emptyCart}</p>
          <Link to="/products" className="btn btn-primary">{t.cart.continueShopping}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container page">
      <h1>{t.checkout.title}</h1>
      <form className="checkout-layout" onSubmit={placeOrder}>
        <div className="checkout-main">
          <section className="card">
            <h3>{t.checkout.contact}</h3>
            {!isAuthenticated && (
              <p className="small muted">
                {t.checkout.guestNote} <Link to="/login" className="link">{t.checkout.signInPrompt}</Link>
              </p>
            )}
            <div className="grid-2">
              <label className="field">
                <span>{t.checkout.fullName}</span>
                <input required value={contact.fullName} onChange={(e) => setContact({ ...contact, fullName: e.target.value })} />
              </label>
              <label className="field">
                <span>{t.checkout.phone}</span>
                <input required type="tel" dir="ltr" value={contact.phone} onChange={(e) => setContact({ ...contact, phone: e.target.value })} />
              </label>
              <label className="field span-2">
                <span>{t.checkout.email}</span>
                <input required type="email" dir="ltr" value={contact.email} onChange={(e) => setContact({ ...contact, email: e.target.value })} />
              </label>
            </div>
          </section>

          <section className="card">
            <h3>{t.checkout.delivery}</h3>
            <div className="options">
              <Option name="delivery" value="HOME_DELIVERY" checked={!pickup} onChange={() => setDeliveryMethod('HOME_DELIVERY')}
                title={t.checkout.homeDelivery} text={t.checkout.homeDeliveryText} />
              <Option name="delivery" value="SHOP_PICKUP" checked={pickup} onChange={() => setDeliveryMethod('SHOP_PICKUP')}
                title={t.checkout.shopPickup} text={t.checkout.shopPickupText} />
            </div>
            {!pickup && (
              <div className="address-block">
                {addresses.length > 0 && (
                  <label className="field">
                    <span>{t.checkout.savedAddresses}</span>
                    <select value={addressChoice} onChange={(e) => setAddressChoice(e.target.value)}>
                      {addresses.map((a) => (
                        <option key={a.id} value={a.id}>
                          {(a.label ? a.label + ' — ' : '') + formatAddress(a)}
                        </option>
                      ))}
                      <option value="new">{t.checkout.useNewAddress}</option>
                    </select>
                  </label>
                )}
                {addressChoice === 'new' && (
                  <label className="field">
                    <span>{t.checkout.address}</span>
                    <textarea rows="3" required value={newAddress} onChange={(e) => setNewAddress(e.target.value)} />
                  </label>
                )}
              </div>
            )}
          </section>

          <section className="card">
            <h3>{t.checkout.payment}</h3>
            <div className="options">
              <Option name="payment" value="ONLINE_PAYMENT" checked={paymentMethod === 'ONLINE_PAYMENT'}
                onChange={() => setPaymentMethod('ONLINE_PAYMENT')} disabled={!payConfig.enabled}
                title={t.checkout.online} text={payConfig.enabled ? t.checkout.onlineText : t.checkout.onlineUnavailable} />
              <Option name="payment" value="CASH_ON_DELIVERY" checked={paymentMethod === 'CASH_ON_DELIVERY'}
                onChange={() => setPaymentMethod('CASH_ON_DELIVERY')}
                title={pickup ? t.checkout.codPickup : t.checkout.cod} text={pickup ? t.checkout.codPickupText : t.checkout.codText} />
              <Option name="payment" value="POS_ON_DELIVERY" checked={paymentMethod === 'POS_ON_DELIVERY'}
                onChange={() => setPaymentMethod('POS_ON_DELIVERY')}
                title={pickup ? t.checkout.posPickup : t.checkout.pos} text={pickup ? t.checkout.posPickupText : t.checkout.posText} />
            </div>
          </section>
        </div>

        <aside className="summary card">
          <h3>{t.checkout.summary}</h3>
          <ul className="summary-items">
            {items.map(({ product, quantity }) => (
              <li key={product.id}>
                <span>{localized(product, 'name', lang)} <span className="muted">× {quantity}</span></span>
                <span>{formatPrice(product.price * quantity, lang)}</span>
              </li>
            ))}
          </ul>
          <div className="summary-row total"><span>{t.common.total}</span><strong>{formatPrice(total, lang)}</strong></div>
          {error && <div className="alert alert-error">{error}</div>}
          <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
            {busy ? t.checkout.placing : paymentMethod === 'ONLINE_PAYMENT' ? t.checkout.payNow : t.checkout.placeOrder}
          </button>
        </aside>
      </form>
    </div>
  );
}
