import { formatAmount, formatPrice, getCurrency, localized, monthRange, setCurrency, todayIso } from './format';

describe('format utilities', () => {
  test('prices are shown in Egyptian pounds by default', () => {
    expect(getCurrency()).toBe('EGP');
    expect(formatPrice(450, 'en')).toMatch(/EGP\s?450\.00/);
  });

  test('Arabic prices keep Latin digits and use the pound sign', () => {
    const ar = formatPrice(1250.5, 'ar');
    expect(ar).toContain('1,250.50');
    expect(ar).toContain('ج.م');
  });

  test('currency from the server config is applied (case-insensitive)', () => {
    setCurrency('usd');
    expect(formatPrice(10, 'en')).toContain('$10.00');
    setCurrency('egp');
    expect(getCurrency()).toBe('EGP');
    setCurrency(null);
    expect(getCurrency()).toBe('EGP');
  });

  test('missing amounts format as zero', () => {
    expect(formatPrice(undefined, 'en')).toMatch(/0\.00/);
    expect(formatAmount(null, 'en')).toBe('0.00');
    expect(formatAmount(1234.5, 'en')).toBe('1,234.50');
  });

  test('localized() prefers Arabic only when it exists', () => {
    const p = { name: 'Gloves', nameAr: 'قفازات', description: 'Blue', descriptionAr: '  ' };
    expect(localized(p, 'name', 'ar')).toBe('قفازات');
    expect(localized(p, 'name', 'en')).toBe('Gloves');
    expect(localized(p, 'description', 'ar')).toBe('Blue');
    expect(localized(null, 'name', 'ar')).toBe('');
  });

  test('monthRange() covers the whole month, including leap years', () => {
    expect(monthRange('2026-10')).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
    expect(monthRange('2028-02')).toEqual({ from: '2028-02-01', to: '2028-02-29' });
  });

  test('todayIso() is a local yyyy-mm-dd date', () => {
    const d = new Date();
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(todayIso().slice(8)).toBe(String(d.getDate()).padStart(2, '0'));
  });
});
