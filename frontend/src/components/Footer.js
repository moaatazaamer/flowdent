import { useLang } from '../context/LangContext';
import Logo from './Logo';

export default function Footer() {
  const { t } = useLang();
  return (
    <footer className="footer">
      <div className="container footer-inner">
        <div className="brand">
          <Logo size={26} />
          <span className="brand-name">{t.brand}</span>
        </div>
        <p className="muted">{t.tagline}</p>
        <p className="muted small">© {new Date().getFullYear()} {t.brand}</p>
      </div>
    </footer>
  );
}
