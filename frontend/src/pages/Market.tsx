import { useMemo, useState } from 'react';
import { api } from '../api';
import { useLoad, useStoredState } from '../hooks';
import type { BarsResponse, Quote, Timeframe } from '../types';
import { Card, ErrorBox } from '../components/ui';
import { PriceChart } from '../components/PriceChart';
import { fmtNum, fmtPct, isoDate, signClass } from '../format';

const TIMEFRAMES: { value: Timeframe; label: string; days: number }[] = [
  { value: 'MIN_15', label: '15分钟', days: 10 },
  { value: 'HOUR_1', label: '1小时', days: 60 },
  { value: 'DAY_1', label: '日线', days: 365 },
  { value: 'WEEK_1', label: '周线', days: 365 * 5 },
];

const SMA = [20, 50];

export function MarketPage() {
  const [watchlist, setWatchlist] = useStoredState<string[]>('watchlist', ['SPY', 'QQQ', 'AAPL', 'MSFT', 'NVDA', 'TSLA']);
  const [symbol, setSymbol] = useState(watchlist[0] ?? 'SPY');
  const [timeframe, setTimeframe] = useState<Timeframe>('DAY_1');
  const [input, setInput] = useState('');

  const quotes = useLoad(
    () => (watchlist.length ? api.get<Record<string, Quote>>(`/api/market/quotes?symbols=${watchlist.join(',')}`) : Promise.resolve({} as Record<string, Quote>)),
    [watchlist.join(',')],
  );

  const tf = TIMEFRAMES.find((t) => t.value === timeframe)!;
  const bars = useLoad(() => {
    const to = new Date();
    const from = new Date(to.getTime() - tf.days * 86400_000);
    return api.get<BarsResponse>(
      `/api/market/bars/${encodeURIComponent(symbol)}?timeframe=${timeframe}&from=${isoDate(from)}&to=${isoDate(to)}`,
    );
  }, [symbol, timeframe]);

  const last = useMemo(() => {
    const b = bars.data?.bars;
    return b && b.length ? b[b.length - 1] : undefined;
  }, [bars.data]);

  const add = (e: React.FormEvent) => {
    e.preventDefault();
    const s = input.trim().toUpperCase();
    if (!s) return;
    if (!watchlist.includes(s)) setWatchlist([...watchlist, s]);
    setSymbol(s);
    setInput('');
  };

  return (
    <div className="grid market">
      <Card title="自选股" actions={<button className="ghost" onClick={() => quotes.reload()}>刷新</button>}>
        <form className="inline" onSubmit={add}>
          <input placeholder="输入代码，如 AMZN" value={input} onChange={(e) => setInput(e.target.value)} />
          <button type="submit">添加</button>
        </form>
        <ErrorBox error={quotes.error} />
        <table className="table watchlist">
          <tbody>
            {watchlist.map((s) => {
              const q = quotes.data?.[s];
              return (
                <tr key={s} className={s === symbol ? 'selected' : ''} onClick={() => setSymbol(s)}>
                  <td className="sym">{s}</td>
                  <td className="num">{q ? fmtNum(q.price) : '—'}</td>
                  <td className={`num ${signClass(q?.changePercent)}`}>{q ? fmtPct(q.changePercent) : ''}</td>
                  <td>
                    <button
                      className="icon"
                      aria-label={`移除 ${s}`}
                      onClick={(e) => { e.stopPropagation(); setWatchlist(watchlist.filter((w) => w !== s)); }}
                    >×</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>

      <Card
        title={<>{symbol} {last && <span className="muted small">收盘 {fmtNum(last.close)}</span>}</>}
        actions={
          <div className="segmented">
            {TIMEFRAMES.map((t) => (
              <button key={t.value} className={t.value === timeframe ? 'active' : ''} onClick={() => setTimeframe(t.value)}>
                {t.label}
              </button>
            ))}
          </div>
        }
      >
        <ErrorBox error={bars.error} />
        {bars.data && bars.data.bars.length > 0 && <PriceChart bars={bars.data.bars} smaPeriods={SMA} />}
        {bars.data && bars.data.bars.length === 0 && <p className="muted">没有数据（可能是非交易时段或代码无效）。</p>}
        <p className="muted small">蜡烛图 + MA{SMA.join(' / MA')} + 成交量。数据源：{bars.data?.source ?? '—'}</p>
      </Card>
    </div>
  );
}
