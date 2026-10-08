import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import en from '../i18n/en';
import ar from '../i18n/ar';

const STORAGE_KEY = 'flowdent_lang';
const DICTS = { en, ar };

const LangContext = createContext(null);

function initialLang() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'ar' || saved === 'en') return saved;
  } catch {
    /* storage unavailable */
  }
  return 'en';
}

export function LangProvider({ children }) {
  const [lang, setLang] = useState(initialLang);
  const t = DICTS[lang];

  useEffect(() => {
    document.documentElement.setAttribute('dir', t.dir);
    document.documentElement.setAttribute('lang', t.lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      /* ignore */
    }
  }, [lang, t]);

  const switchLang = useCallback((next) => {
    setLang((cur) => (next === 'en' || next === 'ar' ? next : cur === 'en' ? 'ar' : 'en'));
  }, []);

  const value = useMemo(
    () => ({ lang, t, switchLang, isRtl: t.dir === 'rtl' }),
    [lang, t, switchLang]
  );

  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang() {
  const ctx = useContext(LangContext);
  if (!ctx) throw new Error('useLang must be used inside LangProvider');
  return ctx;
}
