import axios from 'axios';

export const TOKEN_KEY = 'flowdent_token';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Let AuthContext react to expired / invalid tokens.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err?.response?.status === 401 && localStorage.getItem(TOKEN_KEY)) {
      window.dispatchEvent(new Event('flowdent:unauthorized'));
    }
    return Promise.reject(err);
  }
);

/** Extracts a human-readable message from an API error. */
export function errorMessage(err, fallback) {
  return err?.response?.data?.message || fallback || err?.message || 'Error';
}

/** Downloads a binary response (e.g. an Excel file) through the authenticated client. */
export async function downloadFile(url, filename) {
  const res = await api.get(url, { responseType: 'blob' });
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}

export default api;
