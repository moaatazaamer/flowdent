import { brandAsset } from '../config/company';

/**
 * Flow Dent logo lockup: tooth mark + "flow dent" wordmark (from the company logo).
 * variant="white" is for dark backgrounds; wordmark={false} shows the tooth only.
 */
export default function Logo({ size = 40, variant = 'color', wordmark = true }) {
  const suffix = variant === 'white' ? '-white' : '';
  return (
    <span className="logo-lockup">
      <img
        className="logo-mark"
        src={brandAsset(`flowdent-mark${suffix}.png`)}
        alt={wordmark ? '' : 'Flow Dent'}
        style={{ height: size }}
      />
      {wordmark && (
        <img
          className="logo-word"
          src={brandAsset(`flowdent-wordmark${suffix}.png`)}
          alt="Flow Dent"
          style={{ height: Math.round(size * 0.5) }}
        />
      )}
    </span>
  );
}

/** The Ora logo (Ora is Flow Dent's own brand). */
export function OraLogo({ height = 28, variant = 'color' }) {
  return (
    <img
      className="ora-logo"
      src={brandAsset(variant === 'white' ? 'ora-logo-white.png' : 'ora-logo.png')}
      alt="Ora"
      style={{ height }}
    />
  );
}

/** Brand badge for a product: the Ora logo for Ora products, the plain name for others. */
export function BrandBadge({ brand, className = '' }) {
  if (!brand) return null;
  const isOra = brand.trim().toLowerCase() === 'ora';
  return (
    <span className={`brand-badge ${isOra ? 'brand-badge-ora' : ''} ${className}`}>
      {isOra ? <OraLogo height={14} /> : brand}
    </span>
  );
}
