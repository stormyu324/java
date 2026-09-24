const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export const fmtUsd = (v: number | null | undefined) => (v == null ? '—' : usd.format(Number(v)));

export const fmtNum = (v: number | null | undefined, digits = 2) =>
  v == null ? '—' : Number(v).toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });

export const fmtPct = (v: number | null | undefined, digits = 2) =>
  v == null ? '—' : `${Number(v) >= 0 ? '+' : ''}${Number(v).toFixed(digits)}%`;

export const fmtTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : '—';

export const fmtDate = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleDateString('zh-CN') : '—';

/** US convention in this app: green up, red down. */
export const signClass = (v: number | null | undefined) =>
  v == null || Number(v) === 0 ? '' : Number(v) > 0 ? 'up' : 'down';

export const isoDate = (d: Date) => d.toISOString().slice(0, 10);
