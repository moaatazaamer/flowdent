import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api, { TOKEN_KEY } from '../api';

const USER_KEY = 'flowdent_user';
const AuthContext = createContext(null);

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState(readUser);
  const [ready, setReady] = useState(!localStorage.getItem(TOKEN_KEY));

  const persist = useCallback((newToken, newUser) => {
    if (newToken) localStorage.setItem(TOKEN_KEY, newToken);
    else localStorage.removeItem(TOKEN_KEY);
    if (newUser) localStorage.setItem(USER_KEY, JSON.stringify(newUser));
    else localStorage.removeItem(USER_KEY);
    setToken(newToken || null);
    setUser(newUser || null);
  }, []);

  const logout = useCallback(() => {
    api.post('/auth/logout').catch(() => {});
    persist(null, null);
  }, [persist]);

  // Validate a stored token on load.
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    api
      .get('/auth/me')
      .then((res) => !cancelled && persist(token, res.data))
      .catch(() => !cancelled && persist(null, null))
      .finally(() => !cancelled && setReady(true));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const onUnauthorized = () => persist(null, null);
    window.addEventListener('flowdent:unauthorized', onUnauthorized);
    return () => window.removeEventListener('flowdent:unauthorized', onUnauthorized);
  }, [persist]);

  const login = useCallback(
    async (email, password) => {
      const res = await api.post('/auth/login', { email, password });
      persist(res.data.token, res.data.user);
      return res.data.user;
    },
    [persist]
  );

  const register = useCallback(
    async (payload) => {
      const res = await api.post('/auth/register', payload);
      persist(res.data.token, res.data.user);
      return res.data.user;
    },
    [persist]
  );

  const updateUser = useCallback((u) => persist(token, u), [persist, token]);

  const value = useMemo(() => {
    const role = user?.role;
    return {
      user,
      token,
      ready,
      isAuthenticated: !!token && !!user,
      isAdmin: role === 'ADMIN',
      isStaff: role === 'ADMIN' || role === 'ORDER_ADMIN',
      login,
      register,
      logout,
      updateUser,
    };
  }, [user, token, ready, login, register, logout, updateUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
