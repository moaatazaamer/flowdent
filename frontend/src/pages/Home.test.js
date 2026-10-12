import { screen, within } from '@testing-library/react';
import Home from './Home';
import en from '../i18n/en';
import ar from '../i18n/ar';
import { COMPANY } from '../config/company';
import { mockApi, renderWithProviders } from '../test-utils';

const cat = { id: 1, name: 'Restorative', nameAr: 'ترميم', description: 'Composites' };
const items = [
  { id: 1, name: 'Composite A2', price: 845, stockQuantity: 5, brand: 'Ora', category: cat },
  { id: 2, name: 'Bond (sold out)', price: 1225, stockQuantity: 0, brand: 'Ora', category: cat },
  { id: 3, name: 'Gloves', price: 495, stockQuantity: 5, brand: null, category: { id: 2, name: 'Gloves' } },
];

describe('Home page', () => {
  test('hero, own-brand (Ora) link and the company contact band', async () => {
    mockApi({ get: { '/categories': [cat], '/products': items } });
    renderWithProviders(<Home />);

    expect(screen.getByText(en.home.heroTitle)).toBeInTheDocument();
    expect(screen.getByText(en.tagline)).toBeInTheDocument();
    const ora = screen.getByText(en.home.ownBrand).closest('a');
    expect(ora).toHaveAttribute('href', '/ora');

    const strip = document.querySelector('.hero-contact');
    expect(within(strip).getByText(COMPANY.address.en)).toBeInTheDocument();
    expect(within(strip).getByText('01000709694')).toHaveAttribute('href', 'tel:+201000709694');
    expect(within(strip).getByText('01030044554')).toHaveAttribute('href', 'tel:+201030044554');
    expect(within(strip).getByText(COMPANY.email).closest('a')).toHaveAttribute('href', 'mailto:Flow.dent92@yahoo.com');
    await screen.findByText(en.home.oraTitle);   // catalogue loaded
  });

  test('Ora section lists Ora products only, in-stock first, and links to the Ora page', async () => {
    mockApi({ get: { '/categories': [cat], '/products': [items[1], items[2], items[0]] } });
    renderWithProviders(<Home />);
    const section = (await screen.findByText(en.home.oraTitle)).closest('section');
    const titles = [...section.querySelectorAll('.product-title')].map((a) => a.textContent);
    expect(titles).toEqual(['Composite A2', 'Bond (sold out)']);
    expect(within(section).queryByText('Gloves')).not.toBeInTheDocument();
    expect(within(section).getByText(en.home.oraCta)).toHaveAttribute('href', '/ora');
  });

  test('no Ora section when there are no Ora products; Arabic address in Arabic', async () => {
    localStorage.setItem('flowdent_lang', 'ar');
    mockApi({ get: { '/categories': [cat], '/products': [items[2]] } });
    renderWithProviders(<Home />);
    await screen.findByText(ar.home.categoriesTitle);
    await screen.findAllByText('Gloves');
    expect(screen.queryByText(ar.home.oraTitle)).not.toBeInTheDocument();
    expect(screen.getByText(COMPANY.address.ar)).toBeInTheDocument();
  });
});
