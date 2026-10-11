import en from './en';
import ar from './ar';

function keyPaths(obj, prefix = '') {
  return Object.entries(obj).flatMap(([k, v]) =>
    v && typeof v === 'object' && !Array.isArray(v) ? keyPaths(v, `${prefix}${k}.`) : [`${prefix}${k}`]
  );
}

function leaves(obj) {
  return Object.values(obj).flatMap((v) => (v && typeof v === 'object' && !Array.isArray(v) ? leaves(v) : [v]));
}

describe('translations', () => {
  test('English and Arabic define exactly the same keys', () => {
    expect(keyPaths(ar).sort()).toEqual(keyPaths(en).sort());
  });

  test('direction and language codes are correct', () => {
    expect(en.dir).toBe('ltr');
    expect(en.lang).toBe('en');
    expect(ar.dir).toBe('rtl');
    expect(ar.lang).toBe('ar');
  });

  test('no empty strings, and every dynamic message returns text', () => {
    for (const dict of [en, ar]) {
      for (const v of leaves(dict)) {
        if (typeof v === 'string') expect(v.trim()).not.toBe('');
        if (typeof v === 'function') expect(typeof v(3)).toBe('string');
      }
    }
  });

  test('every status, payment method and operations enum has a label in both languages', () => {
    const statuses = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'READY_FOR_PICKUP', 'PICKED_UP'];
    const payments = ['ONLINE_PAYMENT', 'CASH_ON_DELIVERY', 'POS_ON_DELIVERY', 'BANK_TRANSFER', 'CREDIT'];
    const movements = ['INITIAL', 'SALE', 'SALE_CANCELLED', 'PURCHASE', 'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'ADJUSTMENT', 'DAMAGE'];
    for (const dict of [en, ar]) {
      statuses.forEach((s) => expect(dict.status[s]).toBeTruthy());
      payments.forEach((p) => expect(dict.paymentMethod[p]).toBeTruthy());
      movements.forEach((m) => expect(dict.ops.movementType[m]).toBeTruthy());
      ['CLINIC', 'DOCTOR', 'LAB', 'HOSPITAL', 'OTHER'].forEach((c) => expect(dict.clientType[c]).toBeTruthy());
      ['ORDERED', 'RECEIVED', 'CANCELLED'].forEach((s) => expect(dict.ops.purchaseStatus[s]).toBeTruthy());
    }
  });

  test('Arabic UI text is actually Arabic', () => {
    expect(ar.nav.cart).toMatch(/[؀-ۿ]/);
    expect(ar.ops.clients).toMatch(/[؀-ۿ]/);
  });
});
