import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { downloadFile, errorMessage } from '../../api';
import { useLang } from '../../context/LangContext';
import { formatDate, formatPrice } from '../../utils/format';

const CSV_TYPES = ['sales', 'purchases', 'expenses', 'client-balances', 'provider-balances'];
const isCsv = (f) => !!f && /\.csv$/i.test(f.name);

function Records({ refreshKey }) {
  const { t, lang } = useLang();
  const [type, setType] = useState('');
  const [q, setQ] = useState('');
  const [data, setData] = useState(null);

  useEffect(() => {
    const params = { limit: 100 };
    if (type) params.type = type;
    if (q.trim()) params.q = q.trim();
    api.get('/admin/legacy/records', { params }).then((r) => setData(r.data)).catch(() => {});
  }, [type, q, refreshKey]);

  const kinds = [
    ['', t.old.all], ['SALE', t.old.kindSale], ['PURCHASE', t.old.kindPurchase], ['EXPENSE', t.old.kindExpense],
    ['CLIENT_BALANCE', t.old.kindClientBalance], ['PROVIDER_BALANCE', t.old.kindProviderBalance],
  ];
  const label = Object.fromEntries(kinds);

  return (
    <section className="card section-gap">
      <h3>{t.old.browse}</h3>
      <div className="chips">
        {kinds.map(([k, l]) => (
          <button key={k || 'all'} className={`chip ${type === k ? 'active' : ''}`} onClick={() => setType(k)}>{l}</button>
        ))}
      </div>
      <input className="search-input" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t.old.search} />
      {!data || data.records.length === 0 ? <p className="muted">{t.old.noRecords}</p> : (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead><tr>
                <th>{t.old.date}</th><th>{t.old.who}</th><th>{t.old.note}</th>
                <th className="num">{t.old.amount}</th><th className="num">{t.old.paid}</th>
              </tr></thead>
              <tbody>
                {data.records.map((r) => (
                  <tr key={r.id}>
                    <td className="nowrap">{formatDate(r.date, lang)}</td>
                    <td>{r.party || '—'}<div className="small muted">{label[r.type]}</div></td>
                    <td>{[r.reference, r.description].filter(Boolean).join(' · ') || '—'}</td>
                    <td className="num">{formatPrice(r.amount, lang)}</td>
                    <td className="num">{r.paid != null ? formatPrice(r.paid, lang) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="small muted">{t.old.showing(data.records.length, data.total)}</p>
        </>
      )}
    </section>
  );
}

export default function OldRecords() {
  const { t, lang } = useLang();
  const fileRef = useRef(null);
  const [file, setFile] = useState(null);
  const [csvType, setCsvType] = useState('sales');
  const [preview, setPreview] = useState(null);
  const [skip, setSkip] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [batches, setBatches] = useState([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const loadBatches = useCallback(() => {
    api.get('/admin/legacy/batches').then((r) => setBatches(r.data)).catch(() => {});
  }, []);
  useEffect(loadBatches, [loadBatches]);

  const form = () => {
    const fd = new FormData();
    fd.append('file', file);
    if (isCsv(file)) fd.append('type', csvType);
    return fd;
  };

  const pick = (e) => {
    setFile(e.target.files[0] || null);
    setPreview(null);
    setDone(false);
    setError('');
    setSkip(false);
  };

  const check = async () => {
    setBusy(true);
    setError('');
    setDone(false);
    try {
      const r = await api.post('/admin/legacy/preview', form());
      setPreview(r.data);
    } catch (err) {
      setPreview(null);
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    setBusy(true);
    setError('');
    try {
      const fd = form();
      fd.append('skipInvalid', String(skip));
      await api.post('/admin/legacy/import', fd);
      setDone(true);
      setPreview(null);
      setFile(null);
      if (fileRef.current) fileRef.current.value = '';
      loadBatches();
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (b) => {
    if (!window.confirm(t.old.removeAsk)) return;
    try {
      await api.delete(`/admin/legacy/batches/${b.id}`);
      loadBatches();
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    }
  };

  const download = () => downloadFile('/admin/legacy/template', 'flowdent-old-records-template.xlsx')
    .catch((err) => setError(errorMessage(err, t.common.error)));

  const kindRows = preview ? [
    ['SALE', t.old.kindSale], ['PURCHASE', t.old.kindPurchase], ['EXPENSE', t.old.kindExpense],
    ['CLIENT_BALANCE', t.old.kindClientBalance], ['PROVIDER_BALANCE', t.old.kindProviderBalance],
  ].filter(([k]) => preview.counts[k] > 0) : [];

  const blocked = preview && !!preview.alreadyImportedAt;
  const canConfirm = preview && !blocked && preview.validRows > 0 && (preview.errorCount === 0 || skip);

  return (
    <div className="admin-page">
      <div className="page-head"><h1>{t.old.title}</h1></div>
      <p className="muted lead">{t.old.intro}</p>

      {error && <div className="alert alert-error">{error}</div>}
      {done && (
        <div className="alert alert-success">
          <strong>{t.old.done}</strong> {t.old.doneText}{' '}
          <Link to="/admin/finance" className="link">{t.old.seeMoney}</Link>
        </div>
      )}

      <div className="admin-grid">
        <section className="card">
          <h3>{t.old.step1}</h3>
          <p className="muted">{t.old.step1Text}</p>
          <button className="btn btn-lg btn-outline" onClick={download}>⬇ {t.old.template}</button>
        </section>

        <section className="card">
          <h3>{t.old.step2}</h3>
          <p className="muted">{t.old.step2Text}</p>
          <input ref={fileRef} type="file" accept=".xlsx,.csv" onChange={pick} aria-label={t.old.choose} />
          {isCsv(file) && (
            <label className="field">
              <span>{t.old.csvType}</span>
              <select value={csvType} onChange={(e) => { setCsvType(e.target.value); setPreview(null); }}>
                {CSV_TYPES.map((k) => <option key={k} value={k}>{t.old.types[k]}</option>)}
              </select>
            </label>
          )}
          <div className="row-actions">
            <button className="btn btn-lg" disabled={!file || busy} onClick={check}>
              {busy && !preview ? t.old.checking : t.old.check}
            </button>
          </div>
        </section>
      </div>

      {preview && (
        <section className="card section-gap">
          <h3>{t.old.step3}</h3>

          {blocked && <div className="alert alert-warn">{t.old.alreadyDone(formatDate(preview.alreadyImportedAt, lang))}</div>}

          {preview.validRows === 0 ? <p className="muted">{t.old.nothing}</p> : (
            <>
              <h4>{t.old.found}</h4>
              <div className="ledger">
                {kindRows.map(([k, l]) => (
                  <div className="summary-row" key={k}>
                    <span>{l} <span className="muted small">({t.old.rows(preview.counts[k])})</span></span>
                    <strong>{formatPrice(preview.totals[k], lang)}</strong>
                  </div>
                ))}
              </div>

              {preview.years.length > 0 && (
                <>
                  <h4>{t.old.byYear}</h4>
                  <div className="table-wrap">
                    <table className="table">
                      <thead><tr>
                        <th>{t.old.year}</th><th className="num">{t.old.kindSale}</th>
                        <th className="num">{t.old.kindPurchase}</th><th className="num">{t.old.kindExpense}</th>
                      </tr></thead>
                      <tbody>
                        {preview.years.map((y) => (
                          <tr key={y.year}>
                            <td>{y.year}</td>
                            <td className="num">{formatPrice(y.sales, lang)}</td>
                            <td className="num">{formatPrice(y.purchases, lang)}</td>
                            <td className="num">{formatPrice(y.expenses, lang)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}

              {(preview.newClients.length > 0 || preview.newProviders.length > 0) && (
                <>
                  <h4>{t.old.newPeople}</h4>
                  {preview.newClients.length > 0 && <p><strong>{t.old.newClients}:</strong> {preview.newClients.join(', ')}</p>}
                  {preview.newProviders.length > 0 && <p><strong>{t.old.newProviders}:</strong> {preview.newProviders.join(', ')}</p>}
                </>
              )}
            </>
          )}

          {preview.errorCount > 0 && (
            <div className="alert alert-error">
              <strong>{t.old.problems(preview.problemRows || preview.errorCount)}</strong>
              <p>{t.old.problemsHelp}</p>
              <ul>{preview.errors.slice(0, 10).map((m, i) => <li key={i}>{m}</li>)}</ul>
              <label className="check">
                <input type="checkbox" checked={skip} onChange={(e) => setSkip(e.target.checked)} /> {t.old.skip}
              </label>
            </div>
          )}

          {preview.warnings.length > 0 && (
            <div className="alert alert-warn">
              <div>
                <strong>{t.old.warnings}</strong>
                <ul>{preview.warnings.slice(0, 8).map((m, i) => <li key={i}>{m}</li>)}</ul>
              </div>
            </div>
          )}

          <p className="small muted">{t.old.historyNote} {t.old.openingNote}</p>
          <button className="btn btn-lg" disabled={!canConfirm || busy} onClick={confirm}>
            {busy ? t.old.confirming : t.old.confirm}
          </button>
        </section>
      )}

      <section className="card section-gap">
        <h3>{t.old.past}</h3>
        {batches.length === 0 ? <p className="muted">{t.old.noPast}</p> : (
          <ul className="rank-list">
            {batches.map((b) => (
              <li key={b.id}>
                <span>
                  <strong>{b.fileName}</strong>
                  <div className="small muted">
                    {t.old.importedOn} {formatDate(b.importedAt, lang)} · {t.old.kindSale} {b.sales} · {t.old.kindPurchase} {b.purchases} · {t.old.kindExpense} {b.expenses}
                  </div>
                </span>
                <button className="btn btn-sm btn-outline" onClick={() => remove(b)}>{t.old.remove}</button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Records refreshKey={refreshKey} />
    </div>
  );
}
