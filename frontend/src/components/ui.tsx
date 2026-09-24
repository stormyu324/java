import type { ReactNode } from 'react';

export function Card({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          <h2>{title}</h2>
          <div className="card-actions">{actions}</div>
        </header>
      )}
      {children}
    </section>
  );
}

export function ErrorBox({ error }: { error?: string }) {
  return error ? <div className="alert error">{error}</div> : null;
}

export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: string }) {
  return (
    <div className="stat">
      <div className="stat-label">{label}</div>
      <div className={`stat-value ${tone ?? ''}`}>{value}</div>
    </div>
  );
}

export function ParamFields({
  defaults, values, onChange,
}: {
  defaults: Record<string, number>;
  values: Record<string, number>;
  onChange: (v: Record<string, number>) => void;
}) {
  const labels: Record<string, string> = {
    fast: '快线周期', slow: '慢线周期', period: 'RSI 周期', lower: '超卖线', upper: '超买线',
    entry: '突破周期 N', exit: '离场周期 M',
  };
  return (
    <>
      {Object.keys(defaults).map((k) => (
        <label key={k}>
          {labels[k] ?? k}
          <input
            type="number"
            value={values[k] ?? defaults[k]}
            onChange={(e) => onChange({ ...values, [k]: Number(e.target.value) })}
          />
        </label>
      ))}
    </>
  );
}
