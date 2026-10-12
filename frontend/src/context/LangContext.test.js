import { act, renderHook } from '@testing-library/react';
import { LangProvider, useLang } from './LangContext';

const wrapper = ({ children }) => <LangProvider>{children}</LangProvider>;

describe('LangContext', () => {
  test('defaults to English, left-to-right', () => {
    const { result } = renderHook(() => useLang(), { wrapper });
    expect(result.current.lang).toBe('en');
    expect(result.current.isRtl).toBe(false);
    expect(document.documentElement.getAttribute('dir')).toBe('ltr');
    expect(document.documentElement.getAttribute('lang')).toBe('en');
  });

  test('switching to Arabic flips the whole page to right-to-left and is remembered', () => {
    const { result } = renderHook(() => useLang(), { wrapper });
    act(() => result.current.switchLang());

    expect(result.current.lang).toBe('ar');
    expect(result.current.isRtl).toBe(true);
    expect(result.current.t.brand).toBe('فلو دنت');
    expect(document.documentElement.getAttribute('dir')).toBe('rtl');
    expect(document.documentElement.getAttribute('lang')).toBe('ar');
    expect(localStorage.getItem('flowdent_lang')).toBe('ar');
  });

  test('restores the saved language and ignores unknown values', () => {
    localStorage.setItem('flowdent_lang', 'ar');
    const { result } = renderHook(() => useLang(), { wrapper });
    expect(result.current.lang).toBe('ar');
    act(() => result.current.switchLang('en'));
    expect(result.current.lang).toBe('en');
    act(() => result.current.switchLang('fr'));
    expect(result.current.lang).toBe('en');
  });
});
