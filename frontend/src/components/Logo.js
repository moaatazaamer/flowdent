export default function Logo({ size = 32 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" className="logo">
      <rect width="64" height="64" rx="16" fill="var(--brand)" />
      <path
        d="M20 18c6-4 10 0 12 0s6-4 12 0c5 4 2 14 0 18-2 5-2 12-5 12-4 0-3-10-7-10s-3 10-7 10c-3 0-3-7-5-12-2-4-5-14 0-18z"
        fill="#fff"
      />
      <path d="M14 50c10-5 26-5 36 0" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" fill="none" />
    </svg>
  );
}
