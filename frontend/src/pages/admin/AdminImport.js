import { useRef, useState } from 'react';
import api, { downloadFile, errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';

function ImportCard({ kind, title }) {
  const { t } = useLang();
  const fileRef = useRef(null);
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const upload = async () => {
    if (!file) return;
    setBusy(true);
    setError('');
    setResult(null);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const r = await api.post(`/admin/import/${kind}`, fd);
      setResult(r.data);
      setFile(null);
      if (fileRef.current) fileRef.current.value = '';
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  const download = (path, name) => downloadFile(path, name).catch((err) => setError(errorMessage(err, t.common.error)));

  return (
    <section className="card import-card">
      <h3>{title}</h3>
      <div className="row-actions">
        <button className="btn btn-sm btn-outline" onClick={() => download(`/admin/import/template/${kind}`, `flowdent-${kind}-template.xlsx`)}>
          ⬇ {t.admin.downloadTemplate}
        </button>
        <button className="btn btn-sm btn-outline" onClick={() => download(`/admin/import/export/${kind}`, `flowdent-${kind}.xlsx`)}>
          ⬇ {t.admin.exportCurrent}
        </button>
      </div>
      <label className="dropzone">
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
        <span>{file ? file.name : t.admin.chooseFile}</span>
      </label>
      <button className="btn btn-primary" disabled={!file || busy} onClick={upload}>
        {busy ? t.admin.importing : t.admin.upload}
      </button>
      {error && <div className="alert alert-error">{error}</div>}
      {result && (
        <div className="import-result">
          <div className="summary-row"><span>{t.admin.created}</span><strong>{result.created}</strong></div>
          <div className="summary-row"><span>{t.admin.updated}</span><strong>{result.updated}</strong></div>
          {result.errors?.length > 0 && (
            <>
              <div className="summary-row"><span>{t.admin.errors}</span><strong>{result.errors.length}</strong></div>
              <ul className="error-list" dir="ltr">
                {result.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  );
}

export default function AdminImport() {
  const { t } = useLang();
  return (
    <div className="admin-page">
      <h1>{t.admin.importTitle}</h1>
      <p className="muted lead">{t.admin.importText}</p>
      <div className="admin-grid">
        <ImportCard kind="categories" title={t.admin.importCategories} />
        <ImportCard kind="products" title={t.admin.importProducts} />
      </div>
    </div>
  );
}
