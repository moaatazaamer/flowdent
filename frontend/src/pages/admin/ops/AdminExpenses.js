import { useCallback, useEffect, useMemo, useState } from 'react';
import api, { errorMessage } from '../../../api';
import { useLang } from '../../../context/LangContext';
import Modal from '../../../components/Modal';
import { formatDate, formatPrice, monthRange, todayIso } from '../../../utils/format';
import { StatTile, useProviders } from './shared';

const CATEGORIES = ['RENT', 'SALARIES', 'UTILITIES', 'TRANSPORT', 'MARKETING', 'MAINTENANCE', 'BANK_FEES', 'TAXES', 'OTHER'];

function ExpenseForm({ open, expense, onClose, onSaved }) {
  const { t } = useLang();
  const { providers } = useProviders();
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm(expense
        ? { date: expense.date, category: expense.category, description: expense.description,
            amount: String(expense.amount), supplierId: expense.supplier ? String(expense.supplier.id) : '' }
        : { date: todayIso(), category: 'RENT', description: '', amount: '', supplierId: '' });
      setError('');
    }
  }, [open, expense]);

  if (!open || !form) return null;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body = { ...form, amount: Number(form.amount), supplierId: form.supplierId ? Number(form.supplierId) : null };
      if (expense) await api.put(`/admin/expenses/${expense.id}`, body);
      else await api.post('/admin/expenses', body);
      onSaved();
      onClose();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={expense ? t.ops.editExpense : t.ops.newExpense}>
      <form onSubmit={submit}>
        <div className="grid-2">
          <label className="field">
            <span>{t.ops.date}</span>
            <input type="date" required value={form.date} onChange={set('date')} />
          </label>
          <label className="field">
            <span>{t.ops.category}</span>
            <select value={form.category} onChange={set('category')}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{t.ops.expenseCategory[c]}</option>)}
            </select>
          </label>
          <label className="field span-2">
            <span>{t.ops.description}</span>
            <input required autoFocus value={form.description} onChange={set('description')} />
          </label>
          <label className="field">
            <span>{t.ops.amount} ({t.ops.egp})</span>
            <input type="number" min="0.01" step="0.01" dir="ltr" required value={form.amount} onChange={set('amount')} />
          </label>
          <label className="field">
            <span>{t.ops.provider} <span className="muted">({t.common.optional})</span></span>
            <select value={form.supplierId} onChange={set('supplierId')}>
              <option value="">{t.ops.none}</option>
              {providers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </label>
        </div>
        <p className="small muted">{t.ops.expenseHint}</p>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="row-actions end">
          <button type="button" className="btn btn-outline" onClick={onClose}>{t.common.cancel}</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? t.common.saving : t.common.save}</button>
        </div>
      </form>
    </Modal>
  );
}

export default function AdminExpenses() {
  const { t, lang } = useLang();
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [category, setCategory] = useState('');
  const [expenses, setExpenses] = useState([]);
  const [editing, setEditing] = useState(null); // null closed, {} new, expense edit
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    api.get('/admin/expenses', { params: month ? monthRange(month) : {} })
      .then((r) => setExpenses(r.data)).catch(() => setError(t.common.error)).finally(() => setLoading(false));
  }, [month, t.common.error]);
  useEffect(load, [load]);

  const visible = useMemo(() => expenses.filter((e) => !category || e.category === category), [expenses, category]);
  const total = visible.reduce((s, e) => s + Number(e.amount), 0);
  const byCat = useMemo(() => {
    const m = {};
    expenses.forEach((e) => { m[e.category] = (m[e.category] || 0) + Number(e.amount); });
    return Object.entries(m).sort((a, b) => b[1] - a[1]);
  }, [expenses]);

  const remove = async (e) => {
    if (!window.confirm(t.common.confirmDelete)) return;
    try {
      await api.delete(`/admin/expenses/${e.id}`);
      load();
    } catch (err) {
      setError(errorMessage(err, t.common.error));
    }
  };

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.expenses}</h1>
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ {t.ops.newExpense}</button>
      </div>

      <div className="toolbar">
        <label className="inline-field">
          <span>{t.ops.month}</span>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </label>
        <button className={`chip ${month ? '' : 'active'}`} onClick={() => setMonth('')}>{t.ops.allMonths}</button>
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">{t.ops.all}</option>
          {CATEGORIES.map((c) => <option key={c} value={c}>{t.ops.expenseCategory[c]}</option>)}
        </select>
      </div>

      <div className="stat-grid stat-grid-3">
        <StatTile label={t.ops.totalExpenses} value={formatPrice(total, lang)} hint={`${visible.length}`} />
        {byCat.slice(0, 2).map(([c, v]) => (
          <StatTile key={c} label={t.ops.expenseCategory[c]} value={formatPrice(v, lang)} />
        ))}
      </div>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>{t.ops.date}</th>
              <th>{t.ops.category}</th>
              <th>{t.ops.description}</th>
              <th>{t.ops.provider}</th>
              <th className="num">{t.ops.amount}</th>
              <th className="end">{t.common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((e) => (
              <tr key={e.id}>
                <td className="nowrap">{formatDate(e.date, lang)}</td>
                <td><span className="pill pill-muted">{t.ops.expenseCategory[e.category]}</span></td>
                <td>{e.description}</td>
                <td>{e.supplier?.name || '—'}</td>
                <td className="num">{formatPrice(e.amount, lang)}</td>
                <td className="end nowrap">
                  <button className="link-btn" onClick={() => setEditing(e)}>{t.common.edit}</button>
                  <button className="link-btn danger" onClick={() => remove(e)}>{t.common.delete}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && visible.length === 0 && <p className="muted pad">{t.ops.noExpenses}</p>}
      </div>

      <ExpenseForm open={!!editing} expense={editing?.id ? editing : null} onClose={() => setEditing(null)} onSaved={load} />
    </div>
  );
}
