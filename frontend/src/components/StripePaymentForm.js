import { useEffect, useMemo, useState } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { CardElement, Elements, useElements, useStripe } from '@stripe/react-stripe-js';
import api, { errorMessage } from '../api';
import { useLang } from '../context/LangContext';
import { formatPrice } from '../utils/format';

const stripeCache = {};
function getStripe(key) {
  if (!stripeCache[key]) stripeCache[key] = loadStripe(key);
  return stripeCache[key];
}

function CardForm({ orderId, clientSecret, paymentIntentId, amount, billing, onSuccess }) {
  const { t, lang } = useLang();
  const stripe = useStripe();
  const elements = useElements();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const pay = async (e) => {
    e.preventDefault();
    if (!stripe || !elements) return;
    setBusy(true);
    setError('');
    const result = await stripe.confirmCardPayment(clientSecret, {
      payment_method: {
        card: elements.getElement(CardElement),
        billing_details: { name: billing?.fullName, email: billing?.email, phone: billing?.phone },
      },
    });
    if (result.error) {
      setError(result.error.message || t.checkout.paymentFailed);
      setBusy(false);
      return;
    }
    try {
      const res = await api.post('/payment/confirm', {
        orderId,
        paymentIntentId: result.paymentIntent?.id || paymentIntentId,
      });
      onSuccess(res.data);
    } catch (err) {
      setError(errorMessage(err, t.checkout.paymentFailed));
      setBusy(false);
    }
  };

  return (
    <form onSubmit={pay} className="stripe-form">
      <label className="field-label">{t.checkout.cardDetails}</label>
      <div className="card-element" dir="ltr">
        <CardElement
          options={{
            hidePostalCode: true,
            style: {
              base: { fontSize: '16px', color: '#0f2a37', '::placeholder': { color: '#8aa1ab' } },
              invalid: { color: '#c0392b' },
            },
          }}
        />
      </div>
      <p className="small muted">{t.checkout.testCardHint}</p>
      {error && <div className="alert alert-error">{error}</div>}
      <button className="btn btn-primary btn-block" disabled={!stripe || busy}>
        {busy ? t.checkout.paying : `${t.checkout.payNow} · ${formatPrice(amount, lang)}`}
      </button>
    </form>
  );
}

/**
 * Creates (or reuses) a Stripe PaymentIntent for an existing order, then collects the card.
 * Props: orderId, publishableKey, billing {fullName,email,phone}, onSuccess(order)
 */
export default function StripePaymentForm({ orderId, publishableKey, billing, onSuccess }) {
  const { t, lang } = useLang();
  const [intent, setIntent] = useState(null);
  const [error, setError] = useState('');
  const stripePromise = useMemo(() => getStripe(publishableKey), [publishableKey]);

  useEffect(() => {
    let cancelled = false;
    api
      .post('/payment/create-intent', { orderId })
      .then((res) => !cancelled && setIntent(res.data))
      .catch((err) => !cancelled && setError(errorMessage(err, t.common.error)));
    return () => {
      cancelled = true;
    };
  }, [orderId, t.common.error]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!intent) return <div className="muted">{t.common.loading}</div>;

  return (
    <Elements stripe={stripePromise} options={{ locale: lang === 'ar' ? 'ar' : 'en' }}>
      <CardForm
        orderId={orderId}
        clientSecret={intent.clientSecret}
        paymentIntentId={intent.paymentIntentId}
        amount={intent.amount}
        billing={billing}
        onSuccess={onSuccess}
      />
    </Elements>
  );
}
