const COLOR_MAP: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  RUNNING: 'blue',
  QUEUED: 'amber',
  CANCELED: 'gray',
  PENDING: 'amber',
  PREVIEW: 'amber',
  APPLIED: 'green',
  ACTIVE: 'green',
  DISABLED: 'gray',
};

export default function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${COLOR_MAP[status] ?? 'gray'}`}>{status}</span>;
}
