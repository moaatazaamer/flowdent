import { useLang } from '../context/LangContext';
import { COMPANY, telHref } from '../config/company';

const ICONS = {
  pin: 'M12 22s7-6.2 7-12a7 7 0 10-14 0c0 5.8 7 12 7 12zm0-9a3 3 0 110-6 3 3 0 010 6z',
  phone: 'M6.6 10.8a15 15 0 006.6 6.6l2.2-2.2a1 1 0 011-.25 11.4 11.4 0 003.6.57 1 1 0 011 1V20a1 1 0 01-1 1A17 17 0 013 4a1 1 0 011-1h3.5a1 1 0 011 1c0 1.25.2 2.45.57 3.6a1 1 0 01-.25 1z',
  mail: 'M4 5h16a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6a1 1 0 011-1zm0 2v.4l8 5.2 8-5.2V7H4z',
};

function Icon({ name }) {
  return (
    <span className="contact-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="16" height="16"><path d={ICONS[name]} fill="currentColor" /></svg>
    </span>
  );
}

/** The address / phones / email band from the company banner. */
export default function ContactStrip({ dark = false }) {
  const { t, lang } = useLang();
  return (
    <div className={`contact-strip ${dark ? 'contact-strip-dark' : ''}`}>
      <a className="contact-item" href={COMPANY.mapsUrl} target="_blank" rel="noreferrer" title={t.contact.openMap}>
        <Icon name="pin" />
        <span><span className="sr-only">{t.contact.address}: </span>{COMPANY.address[lang] || COMPANY.address.en}</span>
      </a>
      <span className="contact-item">
        <Icon name="phone" />
        <span className="sr-only">{t.contact.phone}: </span>
        <span dir="ltr" className="contact-phones">
          {COMPANY.phones.map((p, i) => (
            <span key={p}>
              {i > 0 && <span className="sep"> - </span>}
              <a href={telHref(p)}>{p}</a>
            </span>
          ))}
        </span>
      </span>
      <a className="contact-item" href={`mailto:${COMPANY.email}`}>
        <Icon name="mail" />
        <span className="sr-only">{t.contact.email}: </span>
        <span dir="ltr">{COMPANY.email}</span>
      </a>
    </div>
  );
}
