import { fireEvent, screen, within } from '@testing-library/react';
import Ora from './Ora';
import en from '../i18n/en';
import ar from '../i18n/ar';
import { COMPANY } from '../config/company';
import { mockApi, renderWithProviders } from '../test-utils';

const patient = { id: 1, name: 'Patient Protection', nameAr: 'حماية المريض' };
const barrier = { id: 2, name: 'Barrier Protection', nameAr: 'الحواجز الواقية' };
const items = [
  { id: 1, name: 'Dental Bib', nameAr: 'مريلة أسنان', price: 234, stockQuantity: 0, brand: 'Ora', category: patient },
  { id: 2, name: 'Bib Holder', nameAr: 'حامل المريلة', price: 18.2, stockQuantity: 40, brand: 'Ora', category: patient },
  { id: 3, name: 'Barrier Film', nameAr: 'غشاء حماية', price: 57.2, stockQuantity: 12, brand: 'ora', category: barrier },
  { id: 4, name: 'Other Gloves', nameAr: 'قفازات', price: 495, stockQuantity: 5, brand: 'Acme', category: patient },
  { id: 5, name: 'No-brand Rolls', nameAr: 'لفائف', price: 40, stockQuantity: 5, brand: null, category: barrier },
];
const titles = () => [...document.querySelectorAll('.product-title')].map((a) => a.textContent);

describe('Ora page (own brand)', () => {
  test('shows the brand story and only Ora products, in-stock first', async () => {
    mockApi({ get: { '/products': items } });
    renderWithProviders(<Ora />, { route: '/ora' });

    expect(screen.getByText(en.ora.title)).toBeInTheDocument();
    expect(screen.getByText(en.ora.eyebrow)).toBeInTheDocument();
    en.ora.points.forEach((pt) => expect(screen.getByText(pt.title)).toBeInTheDocument());

    await screen.findByText('Bib Holder');
    expect(titles()).toEqual(['Barrier Film', 'Bib Holder', 'Dental Bib']);   // sold-out last
    expect(screen.getByText(en.ora.count(3))).toBeInTheDocument();
    expect(screen.queryByText('Other Gloves')).not.toBeInTheDocument();
    expect(screen.queryByText('No-brand Rolls')).not.toBeInTheDocument();
    expect(screen.getByText(en.products.outOfStock)).toBeInTheDocument();

    // Contact band for bulk orders
    const band = document.querySelector('.ora-contact');
    expect(within(band).getByText(en.ora.bulkTitle)).toBeInTheDocument();
    expect(within(band).getByText('01000709694')).toHaveAttribute('href', 'tel:+201000709694');
  });

  test('category chips only list categories that have Ora products and filter the range', async () => {
    mockApi({ get: { '/products': items } });
    renderWithProviders(<Ora />, { route: '/ora' });
    await screen.findByText('Bib Holder');

    const chips = [...document.querySelectorAll('.chip')].map((c) => c.textContent);
    expect(chips).toEqual([en.ora.all, 'Barrier Protection', 'Patient Protection']);

    fireEvent.click(screen.getByText('Barrier Protection', { selector: 'button' }));
    expect(titles()).toEqual(['Barrier Film']);
    expect(screen.getByText(en.ora.count(1))).toBeInTheDocument();

    fireEvent.click(screen.getByText(en.ora.all));
    expect(titles()).toHaveLength(3);
  });

  test('empty state, and Arabic text', async () => {
    localStorage.setItem('flowdent_lang', 'ar');
    mockApi({ get: { '/products': [items[3]] } });
    renderWithProviders(<Ora />, { route: '/ora' });
    expect(await screen.findByText(ar.ora.empty)).toBeInTheDocument();
    expect(screen.getByText(ar.ora.title)).toBeInTheDocument();
    expect(screen.getByText(COMPANY.address.ar)).toBeInTheDocument();
  });

  test('a loading error is shown', async () => {
    mockApi({ get: { '/products': new Error('down') } });
    renderWithProviders(<Ora />, { route: '/ora' });
    expect(await screen.findByText(en.common.error)).toBeInTheDocument();
  });
});
