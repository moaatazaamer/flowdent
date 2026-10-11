// The store works in Egyptian pounds; the backend can override via /api/payment/config.
let currency = 'EGP';

export function setCurrency(code) {
  if (code) currency = String(code).toUpperCase();
}

export function getCurrency() {
  return currency;
}

function locale(lang) {
  // Arabic UI, Latin digits for prices and dates (common for clinic invoices).
  return lang === 'ar' ? 'ar-u-nu-latn' : 'en-US';
}

export function formatPrice(value, lang = 'en') {
  const n = Number(value ?? 0);
  try {
    return new Intl.NumberFormat(locale(lang), { style: 'currency', currency }).format(n);
  } catch {
    return `${n.toFixed(2)} ${currency}`;
  }
}

export function formatDate(value, lang = 'en', withTime = false) {
  if (!value) return '';
  const d = new Date(value);
  const opts = withTime
    ? { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }
    : { year: 'numeric', month: 'short', day: 'numeric' };
  return d.toLocaleString(locale(lang), opts);
}

/** Picks the Arabic field when the UI is Arabic and a translation exists. */
export function localized(entity, field, lang) {
  if (!entity) return '';
  if (lang === 'ar') {
    const ar = entity[`${field}Ar`];
    if (ar && String(ar).trim()) return ar;
  }
  return entity[field] || '';
}

export const PLACEHOLDER_IMG =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300"><rect width="400" height="300" fill="#e6f1f4"/><path d="M170 105c12-8 20 0 30 0s18-8 30 0c10 8 4 28 0 36-4 10-4 24-10 24-8 0-6-20-20-20s-12 20-20 20c-6 0-6-14-10-24-4-8-10-28 0-36z" fill="#9cc3cf"/></svg>`
  );

export function imgFallback(e) {
  if (e.currentTarget.src !== PLACEHOLDER_IMG) e.currentTarget.src = PLACEHOLDER_IMG;
}

/** Plain number with 2 decimals and no currency symbol (for dense tables). */
export function formatAmount(value, lang = 'en') {
  const n = Number(value ?? 0);
  return new Intl.NumberFormat(locale(lang), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
}

/** Today's date as yyyy-mm-dd in the local time zone (for <input type="date">). */
export function todayIso() {
  const d = new Date();
  const pad = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** First and last day of a yyyy-mm month string. */
export function monthRange(ym) {
  const [y, m] = ym.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return { from: `${ym}-01`, to: `${ym}-${String(last).padStart(2, '0')}` };
}
