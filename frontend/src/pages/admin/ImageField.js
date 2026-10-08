import { useRef, useState } from 'react';
import api, { errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';
import { imgFallback, PLACEHOLDER_IMG } from '../../utils/format';

/** Image URL input with an "upload" button (POST /api/images/upload, max 5MB). */
export default function ImageField({ value, onChange }) {
  const { t } = useLang();
  const fileRef = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const upload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setError('Max 5MB');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const fd = new FormData();
      fd.append('file', file);
      const r = await api.post('/images/upload', fd);
      onChange(r.data.url);
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="image-field">
      <img className="image-preview" src={value || PLACEHOLDER_IMG} alt="" onError={imgFallback} />
      <div className="image-field-controls">
        <label className="field">
          <span>{t.admin.imageUrl}</span>
          <input dir="ltr" value={value} onChange={(e) => onChange(e.target.value)} placeholder="https://… or /api/images/…" />
        </label>
        <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/gif,image/webp" hidden onChange={upload} />
        <button type="button" className="btn btn-sm btn-outline" onClick={() => fileRef.current?.click()} disabled={busy}>
          {busy ? t.admin.uploading : t.admin.uploadImage}
        </button>
        {error && <div className="small error-text">{error}</div>}
      </div>
    </div>
  );
}
