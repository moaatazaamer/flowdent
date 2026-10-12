import { Link } from 'react-router-dom';
import { useLang } from '../context/LangContext';
import Logo, { OraLogo } from './Logo';
import ContactStrip from './ContactStrip';

export default function Footer() {
  const { t } = useLang();
  return (
    <footer className="footer">
      <div className="container footer-top">
        <div className="footer-brand">
          <Logo size={46} variant="white" />
          <p className="footer-tagline">{t.tagline}</p>
        </div>
        <Link to="/products?brand=Ora" className="footer-agent">
          <span>{t.home.agentOf}</span>
          <OraLogo height={30} variant="white" />
        </Link>
      </div>
      <div className="container">
        <ContactStrip dark />
        <p className="footer-copy small">© {new Date().getFullYear()} {t.brand}</p>
      </div>
    </footer>
  );
}
