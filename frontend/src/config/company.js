/* Company details shown across the site. Edit here to update everywhere. */

export const COMPANY = {
  name: 'Flow Dent',
  phones: ['01000709694', '01030044554'],
  email: 'Flow.dent92@yahoo.com',
  address: {
    en: 'Tagore St., opposite Ghabbour Hospital, Azarita, Alexandria',
    ar: 'شارع طاغور أمام مستشفى غبور، الأزاريطة، الإسكندرية',
  },
  mapsUrl: 'https://www.google.com/maps/search/?api=1&query=' +
    encodeURIComponent('شارع طاغور مستشفى غبور الأزاريطة الإسكندرية'),
  /** Product brand Flow Dent is the exclusive agent for. */
  agentBrand: 'Ora',
};

/** International dialling form for tel: links (Egypt, +20). */
export const telHref = (phone) => `tel:+20${phone.replace(/^0/, '')}`;

/** Path to a logo file in public/brand. */
export const brandAsset = (file) => `${process.env.PUBLIC_URL || ''}/brand/${file}`;
