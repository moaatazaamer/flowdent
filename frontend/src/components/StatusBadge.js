import { useLang } from '../context/LangContext';

export default function StatusBadge({ status }) {
  const { t } = useLang();
  return <span className={`status status-${String(status).toLowerCase()}`}>{t.status[status] || status}</span>;
}
