// Reto opcional 1 — insignia con un color distinto por estado
export default function StatusBadge({ status }) {
  return <span className={`badge badge-${status}`}>{status}</span>
}
