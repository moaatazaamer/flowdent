import { useEffect, useMemo, useState } from 'react';
import api from '../../../api';
import { useLang } from '../../../context/LangContext';
import { formatPrice, todayIso } from '../../../utils/format';
import { StatTile } from './shared';

const pad = (n) => String(n).padStart(2, '0');
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

function presetRange(key) {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  switch (key) {
    case 'lastMonth':
      return { from: iso(new Date(y, m - 1, 1)), to: iso(new Date(y, m, 0)) };
    case 'thisYear':
      return { from: `${y}-01-01`, to: todayIso() };
    case 'last12':
      return { from: iso(new Date(y, m - 11, 1)), to: todayIso() };
    case 'thisMonth':
    default:
      return { from: iso(new Date(y, m, 1)), to: todayIso() };
  }
}

function HBars({ rows, lang }) {
  const max = Math.max(1, ...rows.map((r) => Math.abs(r.value)));
  return (
    <ul className="hbar">
      {rows.map((r) => (
        <li key={r.key}>
          <span className="hbar-label">{r.label}</span>
          <span className="hbar-track"><span className="hbar-fill" style={{ width: `${(Math.abs(r.value) / max) * 100}%` }} /></span>
          <span className="hbar-value wide">{formatPrice(r.value, lang)}</span>
        </li>
      ))}
    </ul>
  );
}

function MonthlyChart({ rows, lang }) {
  const { t } = useLang();
  const max = Math.max(1, ...rows.flatMap((r) => [Number(r.netSales), Number(r.costOfGoods) + Number(r.expenses)]));
  return (
    <>
      <div className="legend small">
        <span><i className="sw sw-sales" /> {t.ops.netSales}</span>
        <span><i className="sw sw-cost" /> {t.ops.costOfGoods} + {t.ops.expenses}</span>
      </div>
      <div className="bar-chart pair-chart">
        {rows.map((r) => {
          const sales = Number(r.netSales);
          const cost = Number(r.costOfGoods) + Number(r.expenses);
          const [y, m] = r.month.split('-').map(Number);
          const label = new Date(y, m - 1, 1).toLocaleString(lang === 'ar' ? 'ar-u-nu-latn' : 'en-US', { month: 'short' });
          return (
            <div key={r.month} className="bar-col" title={`${r.month} · ${t.ops.netProfit}: ${formatPrice(r.netProfit, lang)}`}>
              <div className="bar-track pair">
                <div className="bar bar-sales" style={{ height: `${(Math.max(sales, 0) / max) * 100}%` }} />
                <div className="bar bar-cost" style={{ height: `${(Math.max(cost, 0) / max) * 100}%` }} />
              </div>
              <span className="bar-label">{label}</span>
            </div>
          );
        })}
      </div>
    </>
  );
}

export default function AdminFinance() {
  const { t, lang } = useLang();
  const [preset, setPreset] = useState('thisMonth');
  const [range, setRange] = useState(presetRange('thisMonth'));
  const [data, setData] = useState(null);
  const [monthly, setMonthly] = useState([]);
  const [error, setError] = useState(false);

  useEffect(() => {
    setData(null);
    api.get('/admin/finance/summary', { params: range }).then((r) => setData(r.data)).catch(() => setError(true));
  }, [range]);

  useEffect(() => {
    api.get('/admin/finance/monthly', { params: { months: 12 } }).then((r) => setMonthly(r.data)).catch(() => {});
  }, []);

  const choose = (key) => {
    setPreset(key);
    if (key !== 'custom') setRange(presetRange(key));
  };

  const expenseRows = useMemo(() => data
    ? Object.entries(data.expensesByCategory).filter(([, v]) => Number(v) > 0)
      .sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ key: k, label: t.ops.expenseCategory[k], value: Number(v) }))
    : [], [data, t]);

  const extraRows = data ? [
    { key: 'SHIPPING', label: t.ops.shipping, value: Number(data.extraCostsByType.SHIPPING) },
    { key: 'CUSTOMS', label: t.ops.customs, value: Number(data.extraCostsByType.CUSTOMS) },
    { key: 'OTHER', label: t.ops.otherCost, value: Number(data.extraCostsByType.OTHER) },
  ] : [];

  return (
    <div className="admin-page">
      <div className="page-head">
        <h1>{t.ops.finance}</h1>
      </div>

      <div className="toolbar">
        {['thisMonth', 'lastMonth', 'thisYear', 'last12', 'custom'].map((k) => (
          <button key={k} className={`chip ${preset === k ? 'active' : ''}`} onClick={() => choose(k)}>{t.ops[k]}</button>
        ))}
        {preset === 'custom' && (
          <>
            <label className="inline-field"><span>{t.ops.from}</span>
              <input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
            <label className="inline-field"><span>{t.ops.to}</span>
              <input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
          </>
        )}
      </div>

      {error && <div className="alert alert-error">{t.common.error}</div>}
      {!data ? <p className="muted">{t.common.loading}</p> : (
        <>
          <div className="stat-grid">
            <StatTile label={t.ops.netSales} value={formatPrice(data.netSales, lang)}
              hint={`${t.ops.sales} ${formatPrice(data.sales, lang)} − ${t.ops.returns} ${formatPrice(data.returns, lang)}`} />
            <StatTile label={t.ops.grossProfit} value={formatPrice(data.grossProfit, lang)}
              hint={data.grossMargin != null ? `${data.grossMargin}% ${t.ops.marginShort}` : null} />
            <StatTile label={t.ops.expenses} value={formatPrice(data.expenses, lang)}
              hint={Number(data.writeOffs) > 0 ? `+ ${t.ops.writeOffs} ${formatPrice(data.writeOffs, lang)}` : null} />
            <StatTile label={t.ops.netProfit} value={formatPrice(data.netProfit, lang)}
              tone={Number(data.netProfit) < 0 ? 'bad' : 'ok'} />
          </div>

          <div className="admin-grid">
            <section className="card">
              <h3>{t.ops.period}</h3>
              <div className="ledger">
                <div className="summary-row"><span>{t.ops.sales} ({data.orderCount})</span><span>{formatPrice(data.sales, lang)}</span></div>
                <div className="summary-row"><span>− {t.ops.returns}</span><span>{formatPrice(data.returns, lang)}</span></div>
                <div className="summary-row strong"><span>{t.ops.netSales}</span><span>{formatPrice(data.netSales, lang)}</span></div>
                <div className="summary-row"><span>− {t.ops.costOfGoods}</span><span>{formatPrice(data.costOfGoods, lang)}</span></div>
                <div className="summary-row strong"><span>{t.ops.grossProfit}</span><span>{formatPrice(data.grossProfit, lang)}</span></div>
                <div className="summary-row"><span>− {t.ops.expenses}</span><span>{formatPrice(data.expenses, lang)}</span></div>
                <div className="summary-row"><span>− {t.ops.writeOffs}</span><span>{formatPrice(data.writeOffs, lang)}</span></div>
                <div className="summary-row total"><span>{t.ops.netProfit}</span><strong>{formatPrice(data.netProfit, lang)}</strong></div>
              </div>
              <p className="small muted">{t.ops.financeNote}</p>
            </section>

            <section className="card">
              <h3>{t.ops.position}</h3>
              <div className="ledger">
                <div className="summary-row"><span>{t.ops.clientsOwe}</span><strong>{formatPrice(data.clientsOwe, lang)}</strong></div>
                <div className="summary-row"><span>{t.ops.youOwe}</span><strong>{formatPrice(data.weOweProviders, lang)}</strong></div>
                <div className="summary-row"><span>{t.ops.stockValue}</span><strong>{formatPrice(data.stockValue, lang)}</strong></div>
              </div>
              <h3 className="sub-heading">{t.ops.moneyIn} / {t.ops.moneyOut}</h3>
              <div className="ledger">
                <div className="summary-row"><span>{t.ops.moneyIn}<div className="small muted">{t.ops.moneyInHint}</div></span><span className="pos">{formatPrice(data.moneyIn, lang)}</span></div>
                <div className="summary-row"><span>{t.ops.moneyOut}<div className="small muted">{t.ops.moneyOutHint}</div></span><span className="neg">{formatPrice(data.moneyOut, lang)}</span></div>
              </div>
            </section>

            <section className="card">
              <h3>{t.ops.byCategory}</h3>
              {expenseRows.length ? <HBars rows={expenseRows} lang={lang} /> : <p className="muted">{t.ops.noExpenses}</p>}
            </section>

            <section className="card">
              <h3>{t.ops.extraCostsInPeriod}</h3>
              <HBars rows={extraRows} lang={lang} />
              <div className="summary-row total"><span>{t.ops.purchasesInPeriod}</span><strong>{formatPrice(data.purchases, lang)}</strong></div>
            </section>

            <section className="card span-2">
              <h3>{t.ops.profitByCategory}</h3>
              <div className="table-wrap">
                <table className="table">
                  <thead><tr><th>{t.ops.category}</th><th className="num">{t.ops.sales}</th><th className="num">{t.ops.cost}</th><th className="num">{t.ops.profit}</th><th className="num">{t.ops.margin}</th></tr></thead>
                  <tbody>
                    {data.profitByCategory.map((r) => (
                      <tr key={r.category}>
                        <td>{r.category}</td>
                        <td className="num">{formatPrice(r.sales, lang)}</td>
                        <td className="num">{formatPrice(r.cost, lang)}</td>
                        <td className="num">{formatPrice(r.profit, lang)}</td>
                        <td className="num">{Number(r.sales) > 0 ? `${((Number(r.profit) / Number(r.sales)) * 100).toFixed(1)}%` : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {data.profitByCategory.length === 0 && <p className="muted pad">{t.common.noResults}</p>}
              </div>
            </section>
          </div>
        </>
      )}

      {monthly.length > 0 && (
        <section className="card section-gap">
          <h3>{t.ops.monthly}</h3>
          <MonthlyChart rows={monthly} lang={lang} />
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>{t.ops.month}</th><th className="num">{t.ops.netSales}</th><th className="num">{t.ops.costOfGoods}</th><th className="num">{t.ops.expenses}</th><th className="num">{t.ops.extraCosts}</th><th className="num">{t.ops.netProfit}</th></tr></thead>
              <tbody>
                {[...monthly].reverse().map((r) => (
                  <tr key={r.month}>
                    <td className="nowrap">{r.month}</td>
                    <td className="num">{formatPrice(r.netSales, lang)}</td>
                    <td className="num">{formatPrice(r.costOfGoods, lang)}</td>
                    <td className="num">{formatPrice(r.expenses, lang)}</td>
                    <td className="num">{formatPrice(r.extraCosts, lang)}</td>
                    <td className={`num ${Number(r.netProfit) < 0 ? 'neg' : ''}`}><strong>{formatPrice(r.netProfit, lang)}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
